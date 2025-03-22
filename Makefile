BUILD_DIR = ./build

resolve:
	mill -i __.test.runMain

test:
	mill -i __.test

compile:
	mill -i __.compile

bsp:
	mill -i mill.bsp.BSP/install

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

help:
	mill -i --help

verilog:
	mkdir -p $(BUILD_DIR)
	mill -i MarCore.runMain Elaborate -td $(BUILD_DIR)

# Installing
install-hooks: commit-msg

commit-msg:
	@echo "Installing Git hooks..."
	cp -R .githooks/* .git/hooks/
	chmod +x .git/hooks/*

.PHONY: install-hooks commit-msg
