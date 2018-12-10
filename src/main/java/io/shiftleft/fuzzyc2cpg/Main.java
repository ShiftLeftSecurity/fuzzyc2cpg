package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.output.protobuf.OutputModuleFactory;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);


    public static void main(String[] args) {

        Options options = new Options();

        options.addOption(Option.builder("i")
                .longOpt("input")
                .hasArgs()
                .desc("comma-separated list of input directories")
                .hasArgs()
                .required()
                .valueSeparator(',')
                .build());

        options.addOption(Option.builder("o")
                .longOpt("output")
                .hasArg()
                .desc("path to output file (default is cpg.bin.zip in your current working dir)")
                .hasArgs()
                .required(false)
                .build());

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            formatter.printHelp("./fuzzyc2cpg.sh", options);
            System.exit(1);
        }

        assert (cmd != null);
        String[] fileAndDirNames = cmd.getOptionValues("i");
        try {
            Fuzzyc2Cpg fuzzyc2Cpg = new Fuzzyc2Cpg(
                    new OutputModuleFactory(
                            cmd.hasOption("o") ? cmd.getOptionValue("o") : "cpg.bin.zip",
                            true, false));
            fuzzyc2Cpg.runAndOutput(fileAndDirNames);
        } catch (Exception exception) {
            logger.error("Failed to generate CPG.", exception);
            System.exit(1);
        }
        System.exit(0);
    }

}
