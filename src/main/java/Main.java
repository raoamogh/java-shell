import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.List;
import java.io.PrintStream;
import java.io.FileOutputStream;


class Job{
    int id;
    Process process;
    String cmd;
    String status;

    Job(int id, Process process, String cmd){
        this.id = id;
        this.process = process;
        this.cmd = cmd;
        this.status = "Running";
    }
}

public class Main {
    public static String findCmd(String cmd){
        String path = System.getenv("PATH");

        String[] dirs = path.split(":");

        for(String dir : dirs){
            File file = new File(dir, cmd);

            if(file.isFile() && file.canExecute()){
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    public static List<String> parseCmd(String input){
        List<String> parts = new ArrayList<>();
        StringBuilder curr = new StringBuilder();

        boolean isSingleQuotes = false;
        boolean isDoubleQuotes = false;

        boolean escaped = false;

        for(char c : input.toCharArray()){
            if(escaped){
                curr.append(c);
                escaped = false;
            } else if(c == '\\' && !isSingleQuotes){
                escaped = true;
            }else if(c == '\'' && !isDoubleQuotes){
                isSingleQuotes = !isSingleQuotes;
            } else if(c == '"' && !isSingleQuotes ){
                isDoubleQuotes = !isDoubleQuotes;
            }else if(c == ' ' && !isSingleQuotes && !isDoubleQuotes){
                if(curr.length() > 0){
                    parts.add(curr.toString());
                    curr.setLength(0);
                }
            } else {
                curr.append(c);
            }
        }

        if(curr.length() > 0){
            parts.add(curr.toString());
        }

        return parts;
    }
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        String currDir = System.getProperty("user.dir");
        List<Job> jobs = new ArrayList<>();
        while(true){
            System.out.print("$ ");
            String input = sc.nextLine();
            PrintStream out = System.out;
            PrintStream err = System.err;

            List<String> parts = parseCmd(input);
            String outputFile = null;
            String errorFile = null;
            boolean appendOutput = false;
            boolean appendError = false;

            for(int i = 0; i < parts.size(); i++){
                if(parts.get(i).equals(">") || parts.get(i).equals("1>")){
                    outputFile = parts.get(i+1);
                    appendOutput = false;
                    parts = new ArrayList<>(parts.subList(0, i));
                    break;
                } else if(parts.get(i).equals(">>") || parts.get(i).equals("1>>")){
                    outputFile = parts.get(i+1);
                    appendOutput = true;
                    parts = new ArrayList<>(parts.subList(0, i));
                    break;
                } else if(parts.get(i).equals("2>>")){
                    errorFile = parts.get(i+1);
                    appendError = true;
                    parts = new ArrayList<>(parts.subList(0, i));
                    break;
                } else if(parts.get(i).equals("2>")) {
                    errorFile = parts.get(i+1);
                    appendError = false;
                    parts = new ArrayList<>(parts.subList(0, i));
                    break;
                }
            }

            boolean bg = false;

            if(!parts.isEmpty() && parts.get(parts.size()-1).equals("&")){
                bg = true;
                parts.remove(parts.size() - 1);
            }

            if(outputFile != null){
                try{
                    out = new PrintStream(new FileOutputStream(outputFile, appendOutput));
                } catch(Exception e){
                    e.printStackTrace();
                }
                
            }

            if(errorFile != null){
                try{
                    err = new PrintStream(new FileOutputStream(errorFile, appendError));
                } catch(Exception e){
                    e.printStackTrace();
                }
            }

            if(parts.get(0).equals("echo")){
                for(int i = 1; i < parts.size(); i++){
                    out.print(parts.get(i));

                    if(i != parts.size() - 1){
                        out.print(" ");
                    }
                }

                out.println();
            } else if(parts.get(0).equals("exit")){
                break;
            } else if(parts.get(0).equals("type")){
                String cmd = parts.get(1);

                if(cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd") || cmd.equals("jobs")){
                    out.println(cmd + " is a shell builtin");
                } else {
                    String loc = findCmd(cmd);

                    if(loc != null){
                        out.println(cmd + " is " + loc);
                    } else {
                        err.println(cmd + ": not found");
                    }
                }
            } else if(parts.get(0).equals("pwd")){
                out.println(currDir);
            } else if(parts.get(0).equals("cd")){
                File dir;
                if(parts.get(1).equals("~")){
                    dir = new File(System.getenv("HOME"));
                } else if(new File(parts.get(1)).isAbsolute()){
                    dir = new File(parts.get(1));
                } else {
                    dir = new File(currDir, parts.get(1));
                }

                if(dir.exists() && dir.isDirectory()){
                    try {
                        currDir = dir.getCanonicalPath();
                    } catch(Exception e){
                        e.printStackTrace();
                    }
                } else {
                    err.println("cd: " + parts.get(1) + ": No such file or directory");
                }
            } else if(parts.get(0).equals("jobs")){
                for(Job job :  jobs){
                    if(!job.process.isAlive()){
                        job.status = "Done";
                    }
                }

                for (int i = 0; i < jobs.size(); i++) {
                    Job job = jobs.get(i);
                    char marker = ' ';

                    if (i == jobs.size() - 1) {
                        marker = '+';
                    } else if (i == jobs.size() - 2) {
                        marker = '-';
                    }

                    out.printf(
                        "[%d]%c  %-23s %s%n",
                        job.id,
                        marker,
                        job.status,
                        job.cmd
                    );
                }
            } else {
                String exec = findCmd(parts.get(0));
                if(exec != null){
                    try{
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.inheritIO();
                        if(outputFile != null){
                            if(appendOutput){
                                pb.redirectOutput(
                                    ProcessBuilder.Redirect.appendTo(new File(outputFile))
                                );
                            } else {
                                pb.redirectOutput(new File(outputFile));
                            }
                            
                        }
                        if(errorFile != null){
                            if(appendError){
                                pb.redirectError(
                                    ProcessBuilder.Redirect.appendTo(new File(errorFile))
                                );
                            } else {
                                pb.redirectError(new File(errorFile));
                            }
                        }
                        Process process = pb.start();
                        String command = String.join(" ", parts);
                        if(!bg){
                            process.waitFor();
                        } else {
                            int jobId = jobs.size()+1;
                            jobs.add(new Job(
                                jobId,
                                process,
                                command
                            ));
                            out.println("[" + jobId + "] " + process.pid());
                        }
                        
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                } else {
                    err.println(input + ": command not found");
                }
            } 
        }
    }
}