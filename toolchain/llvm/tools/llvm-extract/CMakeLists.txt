set(LLVM_LINK_COMPONENTS asmparser ipo bitreader bitwriter irreader)

add_llvm_tool(llvm-extract
  llvm-extract.cpp
  )
