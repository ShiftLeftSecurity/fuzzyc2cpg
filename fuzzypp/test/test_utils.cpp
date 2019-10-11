#include "test_utils.hpp"

#include <fstream>

namespace fuzzypp::tests {
    const std::filesystem::path
    create_temp_file(const std::string& file_name, const std::string& content) {
        std::filesystem::path file_path { file_name };
        std::filesystem::path full_path = std::filesystem::temp_directory_path() /= file_path;

        if (full_path.has_parent_path()) std::filesystem::create_directories(full_path.parent_path());

        std::ofstream output { full_path, std::ofstream::trunc };
        output << content;

        return full_path;
    }

    const std::string
    read_file_content(const std::filesystem::path& file_path) {
        std::ifstream input { file_path };
        std::stringstream ss;
        ss << input.rdbuf();
        return ss.str();
    }
}
