import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.List;
import java.io.PrintStream;
import java.io.FileOutputStream;

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
        while(true){
            System.out.print("$ ");
            String input = sc.nextLine();
            PrintStream out = System.out;
            PrintStream err = System.err;

            if(input.equals("exit")){
                break;
            }

            List<String> parts = parseCmd(input);
            String outputFile = null;
            String errorFile = null;
            boolean appendOutput = false;

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
                } else if(parts.get(i).equals("2>")) {
                    errorFile = parts.get(i+1);
                    parts = new ArrayList<>(parts.subList(0, i));
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
                    err = new PrintStream(new FileOutputStream(errorFile));
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
            } else if(parts.get(0).equals("type")){
                String cmd = parts.get(1);

                if(cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd")){
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
            } else {
                String exec = findCmd(parts.get(0));
                if(exec != null){
                    try{
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.inheritIO();
                        if(outputFile != null){
                            pb.redirectOutput(new File(outputFile));
                        }
                        if(errorFile != null){
                            pb.redirectError(new File(errorFile));
                        }
                        Process process = pb.start();
                        process.waitFor();
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