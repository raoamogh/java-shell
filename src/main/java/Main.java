import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        Path currentDirectory = Path.of("").toAbsolutePath().normalize();
        int nextJobNumber = 1;
        List<BackgroundJob> backgroundJobs = new ArrayList<>();

        while (true) {
            reapBackgroundJobs(backgroundJobs, false);

            System.out.print("$ ");
            System.out.flush();

            if (!scanner.hasNextLine()) {
                break;
            }

            String commandLine = scanner.nextLine();
            List<ParsedArgument> parsedArguments = parseArguments(commandLine);

            if (parsedArguments.isEmpty()) {
                continue;
            }

            ParsedArgument lastArgument =
                    parsedArguments.get(parsedArguments.size() - 1);

            boolean runInBackground =
                    parsedArguments.size() > 1 &&
                    lastArgument.value.equals("&") &&
                    !lastArgument.quoted;

            if (runInBackground) {
                parsedArguments.remove(parsedArguments.size() - 1);
            }

            String command = parsedArguments.get(0).value;

            if (command.equals("exit")) {
                break;
            }

            if (command.equals("echo")) {
                List<String> argsList = new ArrayList<>();

                for (int i = 1; i < parsedArguments.size(); i++) {
                    argsList.add(parsedArguments.get(i).value);
                }

                System.out.println(String.join(" ", argsList));
            }
            else if (command.equals("jobs")) {
                reapBackgroundJobs(backgroundJobs, true);
            }
            else if (command.equals("pwd")) {
                System.out.println(currentDirectory);
            }
            else if (command.equals("cd") &&
                     parsedArguments.size() > 1) {

                String directory = parsedArguments.get(1).value;

                String resolvedDirectory =
                        directory.equals("~") &&
                        !parsedArguments.get(1).quoted
                        ? System.getenv("HOME")
                        : directory;

                Path requestedDirectory =
                        Path.of(resolvedDirectory);

                Path targetDirectory =
                        requestedDirectory.isAbsolute()
                        ? requestedDirectory.normalize()
                        : currentDirectory.resolve(
                            requestedDirectory
                          ).normalize();

                if (Files.isDirectory(targetDirectory)) {
                    currentDirectory =
                            targetDirectory
                            .toAbsolutePath()
                            .normalize();
                } else {
                    System.out.println(
                        "cd: " +
                        directory +
                        ": No such file or directory"
                    );
                }
            }
                        else if (command.equals("type") &&
                     parsedArguments.size() > 1) {

                String commandName = parsedArguments.get(1).value;

                if (commandName.equals("echo")
                        || commandName.equals("exit")
                        || commandName.equals("type")
                        || commandName.equals("pwd")
                        || commandName.equals("cd")
                        || commandName.equals("jobs")) {

                    System.out.println(
                        commandName + " is a shell builtin"
                    );
                } else {
                    Path executable =
                            findExecutable(commandName);

                    if (executable != null) {
                        System.out.println(
                            commandName + " is " + executable
                        );
                    } else {
                        System.out.println(
                            commandName + ": not found"
                        );
                    }
                }
            }
            else {

                Path executable = findExecutable(command);

                if (executable == null) {
                    System.out.println(
                        command + ": command not found"
                    );
                } else {

                    List<String> commandParts =
                            new ArrayList<>();

                    commandParts.add(command);

                    File stderrAppendFile = null;

                    for (int i = 1;
                         i < parsedArguments.size();
                         i++) {

                        String value =
                                parsedArguments.get(i).value;

                        /*
                         * Stage UN3:
                         * >>  and 2>>
                         * append stderr to a file
                         */
                        if (value.equals(">>")
                                || value.equals("2>>")) {

                            if (i + 1 < parsedArguments.size()) {

                                stderrAppendFile =
                                    currentDirectory
                                        .resolve(
                                            parsedArguments
                                                .get(++i)
                                                .value
                                        )
                                        .toFile();
                            }

                        } else {
                            commandParts.add(value);
                        }
                    }


                    ProcessBuilder processBuilder =
                            new ProcessBuilder(commandParts);

                    processBuilder.directory(
                            currentDirectory.toFile()
                    );

                    /*
                     * Do not use inheritIO()
                     * because we need separate
                     * control over stderr.
                     */
                    processBuilder.redirectInput(
                            ProcessBuilder.Redirect.INHERIT
                    );

                    processBuilder.redirectOutput(
                            ProcessBuilder.Redirect.INHERIT
                    );


                    if (stderrAppendFile != null) {

                        processBuilder.redirectError(
                            ProcessBuilder.Redirect
                                .appendTo(stderrAppendFile)
                        );

                    } else {

                        processBuilder.redirectError(
                            ProcessBuilder.Redirect.INHERIT
                        );
                    }


                    Process process =
                            processBuilder.start();


                    if (runInBackground) {

                        int jobNumber = nextJobNumber++;

                        backgroundJobs.add(
                            new BackgroundJob(
                                jobNumber,
                                process,
                                commandLine.trim()
                            )
                        );

                        System.out.println(
                            "[" +
                            jobNumber +
                            "] " +
                            process.pid()
                        );

                    } else {

                        process.waitFor();

                    }
                }
            }
        }
    }
        private static void reapBackgroundJobs(
            List<BackgroundJob> backgroundJobs,
            boolean includeRunning)
            throws InterruptedException {

        List<BackgroundJob> completedJobs = new ArrayList<>();

        for (int i = 0; i < backgroundJobs.size(); i++) {

            BackgroundJob job = backgroundJobs.get(i);
            boolean running = job.process.isAlive();

            if (running && !includeRunning) {
                continue;
            }

            char marker =
                    i == backgroundJobs.size() - 1 ? '+'
                    : i == backgroundJobs.size() - 2 ? '-'
                    : ' ';

            String status =
                    running ? "Running" : "Done";

            String displayedCommand =
                    running
                    ? job.commandLine
                    : job.commandLine.replaceFirst("\\s*&\\s*$", "");

            System.out.printf(
                "[%d]%c  %-24s%s%n",
                job.number,
                marker,
                status,
                displayedCommand
            );

            if (!running) {
                job.process.waitFor();
                completedJobs.add(job);
            }
        }

        backgroundJobs.removeAll(completedJobs);
    }


    private static List<ParsedArgument> parseArguments(String input) {

        List<ParsedArgument> arguments = new ArrayList<>();

        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean tokenStarted = false;
        boolean tokenQuoted = false;


        for (int i = 0; i < input.length(); i++) {

            char character = input.charAt(i);


            if (character == '\\'
                    && !inSingleQuotes
                    && !inDoubleQuotes) {

                if (i + 1 < input.length()) {
                    current.append(input.charAt(++i));
                } else {
                    current.append(character);
                }

                tokenStarted = true;
                tokenQuoted = true;
            }


            else if (character == '\\'
                    && inDoubleQuotes) {

                if (i + 1 < input.length()
                        && (input.charAt(i + 1) == '"'
                        || input.charAt(i + 1) == '\\')) {

                    current.append(input.charAt(++i));

                } else {

                    current.append(character);
                }

                tokenStarted = true;
                tokenQuoted = true;
            }


            else if (character == '\''
                    && !inDoubleQuotes) {

                inSingleQuotes = !inSingleQuotes;

                tokenStarted = true;
                tokenQuoted = true;
            }


            else if (character == '"'
                    && !inSingleQuotes) {

                inDoubleQuotes = !inDoubleQuotes;

                tokenStarted = true;
                tokenQuoted = true;
            }


            else if (Character.isWhitespace(character)
                    && !inSingleQuotes
                    && !inDoubleQuotes) {

                if (tokenStarted) {

                    arguments.add(
                        new ParsedArgument(
                            current.toString(),
                            tokenQuoted
                        )
                    );

                    current.setLength(0);

                    tokenStarted = false;
                    tokenQuoted = false;
                }

            } else {

                current.append(character);

                tokenStarted = true;
            }
        }


        if (tokenStarted) {

            arguments.add(
                new ParsedArgument(
                    current.toString(),
                    tokenQuoted
                )
            );
        }

        return arguments;
    }


    private static class ParsedArgument {

        private final String value;
        private final boolean quoted;


        private ParsedArgument(
                String value,
                boolean quoted) {

            this.value = value;
            this.quoted = quoted;
        }
    }


    private static class BackgroundJob {

        private final int number;
        private final Process process;
        private final String commandLine;


        private BackgroundJob(
                int number,
                Process process,
                String commandLine) {

            this.number = number;
            this.process = process;
            this.commandLine = commandLine;
        }
    }


    private static Path findExecutable(String commandName) {

        String pathValue = System.getenv("PATH");

        if (pathValue == null) {
            return null;
        }


        String[] directories =
                pathValue.split(
                    Pattern.quote(File.pathSeparator),
                    -1
                );


        for (String directory : directories) {

            Path candidate =
                    Path.of(directory)
                        .resolve(commandName);


            if (Files.isRegularFile(candidate)
                    && Files.isExecutable(candidate)) {

                return candidate;
            }
        }

        return null;
    }
}