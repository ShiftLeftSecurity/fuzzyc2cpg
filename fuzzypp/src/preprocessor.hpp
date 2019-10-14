#ifndef FUZZYPP_PREPROCESSOR_HPP
#define FUZZYPP_PREPROCESSOR_HPP

#include <string>

#include <simplecpp.h>

#include "cliopts.hpp"

namespace fuzzypp::preprocessor {
    class Preprocessor {
        public:
            static void preprocess(const fuzzypp::cliopts::CliOptions& options);

        private:
            static const simplecpp::DUI
            generate_simplecpp_opts(const fuzzypp::cliopts::CliOptions& options);

            static const std::string 
            stringify(const std::string& filename, const simplecpp::DUI& options);
    };
}

#endif
