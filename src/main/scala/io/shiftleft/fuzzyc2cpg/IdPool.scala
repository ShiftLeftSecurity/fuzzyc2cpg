package io.shiftleft.fuzzyc2cpg

import io.shiftleft.passes.KeyPool

object IdPool extends KeyPool(1, Long.MaxValue) {}
