#include <filesystem>
#include <string>

namespace fuzzypp::tests {
    const std::filesystem::path
    create_temp_file(const std::string& file_name, const std::string& content = "");

    const std::string
    read_file_content(const std::filesystem::path& file_path);
}
