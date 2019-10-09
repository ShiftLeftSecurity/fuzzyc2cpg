#include <iostream>

#include "cliopts.hpp"

namespace fuzzypp::cliopts {
    const std::optional<CliOptions> 
    CliOptions::parse_command_line(int argc, char* argv[]) {
        try {
            cxxopts::Options options { argv[0], " - options" };
            options
                .positional_help("[optional args]")
                .show_positional_help();
            
            options
                .allow_unrecognised_options()
                .add_options()
                ("f,file", "A set of input source files (.c/.cpp).", cxxopts::value<std::vector<std::string>>(), "FILE")
                ("o,output", "A directory the output should be written to", cxxopts::value<std::string>(), "FILE")
                ("I", "A set of paths to include on the header search path", cxxopts::value<std::vector<std::string>>(), "DIR")
                ("include", "A set of files to include on the header search path", cxxopts::value<std::vector<std::string>>(), "FILE")
                ("D,define", "A set of defined values", cxxopts::value<std::vector<std::string>>(), "NAME[=VALUE]")
                ("U,undefine", "A set of undefined values", cxxopts::value<std::vector<std::string>>(), "NAME")
                ("help", "Print help.");

            auto result = options.parse(argc, argv);

            if (result.count("help")) {
                std::cout << options.help() << std::endl;
            }

            auto files = extract_vector(result, "file");
            auto i_paths = extract_vector(result, "I");
            auto i_files = extract_vector(result, "include");
            auto defs = extract_vector(result, "define");
            auto undefs = extract_vector(result, "undefine");
            auto output = result.count("output") ?
                result["output"].as<std::string>() : "";

            return std::optional<CliOptions> {
                CliOptions {
                    files,
                    i_files,
                    i_paths,
                    defs,
                    undefs,
                    output
                }
            };
        } catch (const cxxopts::OptionException& e) {
            std::cerr << "Error occured whilst parsing command line arguments..." << std::endl;
            return std::optional<CliOptions> {};
        }
    }

    inline const std::vector<std::string> 
    CliOptions::extract_vector(const cxxopts::ParseResult& parsed, const std::string& name) {
        return parsed.count(name) ? 
            parsed[name].as<std::vector<std::string>>() : std::vector<std::string>();
    }

    const std::optional<std::string>
    CliOptions::validate_options() const {
        if (files.empty()) {
            return std::optional<std::string> { "At least one input file must be specified." };
        }

        if (output_directory.empty()) {
            return std::optional<std::string> { "The output directory must be specified." };
        }

        if (std::filesystem::exists(output_directory) && !std::filesystem::is_directory(output_directory)) {
            return std::optional<std::string> { "The specified output directory must be a directory." };
        }

        for (const auto& file : files) {
            std::filesystem::path p { file };
            if (!is_path_valid(p)) {
                return std::optional<std::string> { "File paths must not contain '..'." };
            }
            if (!std::filesystem::is_regular_file(p)) {
                return std::optional<std::string> { "File [" + file + "] does not exist." };
            }
        }

        for (const auto& i_file : include_files) {
            std::filesystem::path p { i_file };
            if (!is_path_valid(p)) {
                return std::optional<std::string> { "Include file paths must not contain '..'." };
            }
            if (!std::filesystem::is_regular_file(p)) {
                return std::optional<std::string> { "Include file [" + i_file + "] does not exist." };
            }
        }

        for (const auto& i_path : include_paths) {
            std::filesystem::path p { i_path };
            if (!is_path_valid(p)) {
                return std::optional<std::string> { "Include paths must not contain '..'." };
            }
            if (!std::filesystem::is_directory(p)) {
                return std::optional<std::string> { "Include path [" + i_path + "] does not exist." };
            }
        }

        return std::optional<std::string> {};
    }

    inline bool
    CliOptions::is_path_valid(const std::filesystem::path& path) {
        return path.string().find("..") == std::string::npos;
    }
}
