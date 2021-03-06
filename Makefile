export SHELL=/bin/bash
NJOB := ${shell nproc}
TOOLCHAIN_DIR=$(CURDIR)/toolchain
EMULATOR_DIR=$(CURDIR)/emulator
INSTALL_DIR := $(abspath ${CURDIR}/install)
GCC_SRC := ${TOOLCHAIN_DIR}/gcc
GCC_BUILD := ${TOOLCHAIN_DIR}/gcc/build
LLVM_SRC := ${TOOLCHAIN_DIR}/llvm
LLVM_BUILD := ${TOOLCHAIN_DIR}/llvm-build
FESVR_SRC := ${EMULATOR_DIR}/fesvr
FESVR_BUILD := ${EMULATOR_DIR}/fesvr-build

.PHONY: gcc-build-elf llvm-build fesvr-build

gcc-build-elf: stamp/gcc-build-elf

stamp/gcc-build-elf: stamp/gcc-config
	(cd ${GCC_BUILD}; PATH=${PATH}:${INSTALL_DIR}/bin make)
	mkdir -p $(dir $@) && touch $@

stamp/gcc-config: ${GCC_BUILD} ${INSTALL_DIR}
	(cd ${GCC_BUILD}; \
		../configure --prefix=${INSTALL_DIR})
	mkdir -p $(dir $@) && touch $@

llvm-build: stamp/llvm-config
	(cd ${LLVM_BUILD}; make -j${NJOB}; make install)

stamp/llvm-config: ${LLVM_BUILD} ${INSTALL_DIR}
	(cd ${LLVM_BUILD}; \
		cmake -DLLVM_TARGETS_TO_BUILD='RISCV' \
		-DCMAKE_BUILD_TYPE=Release -DLLVM_ENABLE_ASSERTIONS=1 \
		-DCMAKE_C_COMPILER="/usr/bin/clang" \
		-DCMAKE_CXX_COMPILER="/usr/bin/clang++" \
		-DCMAKE_INSTALL_PREFIX=${INSTALL_DIR} ${LLVM_SRC})
	mkdir -p $(dir $@) && touch $@

fesvr-build: stamp/fesvr-config
	(cd ${FESVR_BUILD}; make install -j${NJOB})

stamp/fesvr-config: ${FESVR_BUILD} ${INSTALL_DIR}
	(cd ${FESVR_BUILD}; \
		${FESVR_SRC}/configure --prefix=${INSTALL_DIR})
	mkdir -p $(dir $@) && touch $@

${INSTALL_DIR}:
	mkdir $@

${GCC_BUILD}:
	mkdir $@

${FESVR_BUILD}:
	mkdir $@

