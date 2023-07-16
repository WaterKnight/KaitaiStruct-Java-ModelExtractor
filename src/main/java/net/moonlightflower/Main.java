package net.moonlightflower;

import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        new CommandLine(new ExtractCommand()).execute(args);
    }
}