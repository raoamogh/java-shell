import java.io.File;
import java.util.Scanner;

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
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        while(true){
            System.out.print("$ ");
            String input = sc.nextLine();
            if(input.equals("exit")){
                break;
            }

            String[] parts = input.split(" ");

            if(parts[0].equals("echo")){
                for(int i = 1; i < parts.length; i++){
                    System.out.print(parts[i]);

                    if(i != parts.length - 1){
                        System.out.print(" ");
                    }
                }

                System.out.println();
            } else if(parts[0].equals("type")){
                String cmd = parts[1];

                if(cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type")){
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    String loc = findCmd(cmd);

                    if(loc != null){
                        System.out.println(cmd + " is " + loc);
                    } else {
                        System.out.println(cmd + ": not found");
                    }
                }
            } else {
                System.out.println(input + ": command not found");
            }
        }
    }
}