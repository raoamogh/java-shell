import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.List;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;


class Job {
    int id;
    Process process;

    String runningCmd;
    String doneCmd;

    boolean done;
    boolean notified;

    Job(int id, Process process, String command) {
        this.id = id;
        this.process = process;

        this.runningCmd = command;
        this.doneCmd = command.replaceAll("\\s*&\\s*$", "");

        this.done = false;
        this.notified = false;
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

    public static void transfer(
        java.io.InputStream in,
        java.io.OutputStream out
    ) throws Exception {

        byte[] buffer = new byte[8192];

        int len;

        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }

        out.close();
        in.close();
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

    public static void updateJobs(List<Job> jobs) {
        for (Job job : jobs) {
            if (!job.done && !job.process.isAlive()) {
                job.done = true;
            }
        }
    }

    public static void notifyDoneJobs(List<Job> jobs) {
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);

            if (job.done && !job.notified) {

                char marker = ' ';

                if (i == jobs.size() - 1) {
                    marker = '+';
                } else if (i == jobs.size() - 2) {
                    marker = '-';
                }

                System.out.printf(
                    "[%d]%c  %-23s %s%n",
                    job.id,
                    marker,
                    "Done",
                    job.doneCmd
                );

                job.notified = true;
            }
        }
    }

    public static void reapJobs(List<Job> jobs) {
        jobs.removeIf(job -> job.done && job.notified);
    }

    public static int getNextJobId(List<Job> jobs) {
        int id = 1;

        while (true) {
            boolean used = false;

            for (Job job : jobs) {
                if (job.id == id) {
                    used = true;
                    break;
                }
            }

            if (!used) {
                return id;
            }

            id++;
        }
    }
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        String currDir = System.getProperty("user.dir");
        List<Job> jobs = new ArrayList<>();
        int nextJobId = 1;
        while(true){
            updateJobs(jobs);
            notifyDoneJobs(jobs);
            System.out.print("$ ");
            String input = sc.nextLine();
            reapJobs(jobs);
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

            int pipeIndex = -1;

            for(int i = 0; i < parts.size(); i++){
                if(parts.get(i).equals("|")) {
                    pipeIndex = i;
                    break;
                }
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

            if(pipeIndex != -1){
                List<String> left = new ArrayList<>(parts.subList(0, pipeIndex));
                List<String> right = new ArrayList<>(parts.subList(pipeIndex+1, parts.size()));
            }
            if(pipeIndex != -1){
                try {
                    List<String> left =
                        new ArrayList<>(parts.subList(0, pipeIndex));

                    List<String> right =
                        new ArrayList<>(parts.subList(pipeIndex + 1, parts.size()));

                    String leftPath = findCmd(left.get(0));
                    String rightPath = findCmd(right.get(0));

                    if(leftPath == null || rightPath == null){
                        err.println("command not found");
                        continue;
                    }

                    left.set(0, leftPath);
                    right.set(0, rightPath);

                    ProcessBuilder leftPB =
                        new ProcessBuilder(left);

                    ProcessBuilder rightPB =
                        new ProcessBuilder(right);
                    
                    rightPB.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    rightPB.redirectError(ProcessBuilder.Redirect.INHERIT);

                    Process leftProcess = leftPB.start();

                    Process rightProcess = rightPB.start();

                    transfer(
                        leftProcess.getInputStream(),
                        rightProcess.getOutputStream()
                    );

                    leftProcess.waitFor();
                    rightProcess.waitFor();

                } catch(Exception e){
                    e.printStackTrace();
                }

                continue;
            } else if(parts.get(0).equals("echo")){
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
            } else if (parts.get(0).equals("jobs")) {

                updateJobs(jobs);

                for (int i = 0; i < jobs.size(); i++) {
                    Job job = jobs.get(i);

                    char marker = ' ';

                    if (i == jobs.size() - 1) {
                        marker = '+';
                    } else if (i == jobs.size() - 2) {
                        marker = '-';
                    }

                    String status = job.done ? "Done" : "Running";

                    String command = job.done ? job.doneCmd : job.runningCmd;

                    out.printf(
                        "[%d]%c  %-23s %s%n",
                        job.id,
                        marker,
                        status,
                        command
                    );

                    if (job.done) {
                        job.notified = true;
                    }
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
                            int jobId = getNextJobId(jobs);
                            jobs.add(new Job(
                                jobId,
                                process,
                                input
                            ));
                            out.println("[" + jobId + "] " + process.pid());

                            nextJobId++;
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