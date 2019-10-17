#ifndef FUZZYPP_CLIOPTS_HPP
#define FUZZYPP_CLIOPTS_HPP

#include <filesystem>
#include <list>
#include <optional>
#include <ostream>
#include <string>

#include <cxxopts.hpp>

namespace fuzzypp::cliopts {
    class CliOptions {
        public:
            const std::vector<std::string> files;
            const std::vector<std::string> include_files;
            const std::vector<std::string> include_paths;
            const std::vector<std::string> defines;
            const std::vector<std::string> undefines;
            const std::string output_directory;
            const bool verbose;

            // TODO: Add a `verbose` option for printing of errors etc.
            CliOptions(const std::vector<std::string> _files,
                       const std::vector<std::string> _include_files,
                       const std::vector<std::string> _include_paths,
                       const std::vector<std::string> _defines,
                       const std::vector<std::string> _undefines,
                       const std::string _output_directory,
                       const bool _verbose) :
                       files(_files), include_files(_include_files), 
                       include_paths(_include_paths), defines(_defines), 
                       undefines(_undefines), output_directory(_output_directory), 
                       verbose(_verbose) {}

            CliOptions(CliOptions&) = delete;
            CliOptions(CliOptions&&) = default;

            static const std::optional<CliOptions> 
            parse_command_line(int argc, char* argv[]);

            const std::optional<std::string>
            validate_options() const;

            friend std::ostream& 
            operator<<(std::ostream& out, const CliOptions& opts);

        private:
            inline static const std::vector<std::string> 
            extract_vector(const cxxopts::ParseResult& parsed, const std::string& name) {
                return parsed.count(name) ? 
                    parsed[name].as<std::vector<std::string>>() : std::vector<std::string>();
            }

            inline static bool
            is_path_valid(const std::filesystem::path& path) {
                return path.string().find("..") == std::string::npos;
            }
    };
}

#endif
