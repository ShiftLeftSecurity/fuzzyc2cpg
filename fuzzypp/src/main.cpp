#include <iostream>

#include "cliopts.hpp"
#include "preprocessor.hpp"

int main(int argc, char* argv[]) {

    auto opts = fuzzypp::cliopts::CliOptions::parse_command_line(argc, argv);

    if (!opts) {
        std::cerr << "Failed to parse command line options." << std::endl;
        return 1;
    }

    auto err = opts->validate_options();

    if (err) {
        std::cerr << "Invalid command line options: " << *err << std::endl;
        return 1;
    }

    fuzzypp::preprocessor::FuzzyPreprocessor::preprocess(*opts);

    return 0;
}
