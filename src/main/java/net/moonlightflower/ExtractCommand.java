package net.moonlightflower;

import picocli.CommandLine;

import java.io.File;
import java.io.IOException;

@CommandLine.Command(
        name = "extract"
)
public class ExtractCommand implements Runnable {
    @CommandLine.Option(names = {"--inputDir", "-i"}, required = true)
    private String inputDir;

    @CommandLine.Option(names = {"--outputDir", "-o"}, required = true)
    private String outputDir;

    @CommandLine.Option(names = {"--inputPackageName", "-ipkg"}, required = true)
    private String inputPackageName;

    @CommandLine.Option(names = {"--outputPackageName", "-opkg"}, required = true)
    private String outputPackageName;

    @Override
    public void run() {
        final Extractor extractor = new Extractor(new File(inputDir), new File(outputDir), inputPackageName, outputPackageName);
        try {
            extractor.exec();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
