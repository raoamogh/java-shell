import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.print("$ ");
        while(true){
            Scanner scanner = new Scanner(System.in);
            String command = scanner.nextLine();
            System.out.println(command + ": command not found");
        }
    }
}
