#include <algorithm>
#include <filesystem>
#include <fstream>

#include <iostream>

#include "preprocessor.hpp"

namespace fuzzypp::preprocessor {
    void 
    Preprocessor::preprocess(const fuzzypp::cliopts::CliOptions& options) {
        const auto output_path = std::filesystem::path { options.output_directory };

        auto write_to_file = [&](const auto& file_name) {
            auto file_path = std::filesystem::path { file_name }.relative_path();
            auto output_file = (std::filesystem::weakly_canonical(output_path) /= file_path).lexically_normal();
            if (output_file.has_parent_path()) std::filesystem::create_directories(output_file.parent_path());
            
            std::ofstream output { output_file, std::ofstream::trunc };
            output << stringify(file_name, options);
        };

        std::for_each(options.files.cbegin(),
                      options.files.cend(),
                      write_to_file);
    }

    const simplecpp::DUI
    Preprocessor::generate_simplecpp_opts(const fuzzypp::cliopts::CliOptions& options) {
        simplecpp::DUI simple_opts;

        simple_opts.includes = std::list<std::string> { options.include_files.cbegin(), options.include_files.cend() };
        simple_opts.includePaths = std::list<std::string> { options.include_paths.cbegin(), options.include_paths.cend() };
        simple_opts.defines = std::list<std::string> { options.defines.cbegin(), options.defines.cend() };
        simple_opts.undefined = std::set<std::string> { options.undefines.cbegin(), options.undefines.cend() };

        return simple_opts;
    }

    const std::string
    Preprocessor::stringify(const std::string& filename, 
                            const fuzzypp::cliopts::CliOptions& options) {
        auto simplecpp_opts = generate_simplecpp_opts(options);

        simplecpp::OutputList output_list;
        std::vector<std::string> files;
        
        std::ifstream input_file { filename };

        simplecpp::TokenList raw_tokens { input_file, files, filename, &output_list };
        simplecpp::TokenList output_tokens { files };

        // Initialising this with simplecpp::load seems to double-include files...
        // Leaving as an empty map for the time being.
        std::map<std::string, simplecpp::TokenList*> included { };

        simplecpp::preprocess(output_tokens, raw_tokens, files, included, simplecpp_opts, &output_list);
        if (options.verbose) print_preprocessor_errors(output_list);
        simplecpp::cleanup(included);

        return output_tokens.stringify();
    }

    void
    Preprocessor::print_preprocessor_errors(const simplecpp::OutputList& output_list) {
        std::cerr << "Preprocessor errors:" << std::endl;
        std::cerr << "====================" << std::endl; 

        for (const auto& output : output_list) {
            std::cerr << "=> " << output.location.file() << ':' << output.location.line << ": ";
        
            switch (output.type) {
                case simplecpp::Output::ERROR:
                    std::cerr << "#error: ";
                    break;
                case simplecpp::Output::WARNING:
                    std::cerr << "#warning: ";
                    break;
                case simplecpp::Output::MISSING_HEADER:
                    std::cerr << "missing header: ";
                    break;
                case simplecpp::Output::INCLUDE_NESTED_TOO_DEEPLY:
                    std::cerr << "include nested too deeply: ";
                    break;
                case simplecpp::Output::SYNTAX_ERROR:
                    std::cerr << "syntax error: ";
                    break;
                case simplecpp::Output::PORTABILITY_BACKSLASH:
                    std::cerr << "portability: ";
                    break;
                case simplecpp::Output::UNHANDLED_CHAR_ERROR:
                    std::cerr << "unhandled char error: ";
                    break;
            }

            std::cerr << output.msg << std::endl;
        }
    }
}
