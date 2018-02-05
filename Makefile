JAVAC := javac
FIND := find
SOURCE_DIR := src
OUTPUT_DIR := classes
JAR := jar

# make-directories - Ensure output directory exists.
make-directories := $(shell mkdir $(OUTPUT_DIR))

# all - Perform all tasks for a complete build
.PHONY: all
all: extractlib compile jar tidy

# all_javas - Temp file for holding source file list
all_javas := $(OUTPUT_DIR)/all.javas

# compile - Compile the source
.PHONY: compile
compile: $(all_javas)
	$(JAVAC) -encoding ISO-8859-1 -cp lib/commons-compress-1.15.jar -d $(OUTPUT_DIR) @$<

# extractlib - Copy classes needed from the Apache commons library
.PHONY: extractlib
extractlib: $(all_javas)
	cd $(OUTPUT_DIR); jar -xf ../lib/commons-compress-1.15.jar org

# all_javas - Gather source file list
.INTERMEDIATE: $(all_javas)
$(all_javas):
	$(FIND) $(SOURCE_DIR) -name '*.java' > $@

jar:
	@echo "Manifest-Version: 1.0" > manifest.txt
	@echo "Class-Path: ." >> manifest.txt
	@echo "Main-Class: org.shetline.timezones.CompactTimeZoneGenerator" >> manifest.txt
	@echo "" >> manifest.txt

	rm $(OUTPUT_DIR)/all.javas
	$(JAR) -cmf manifest.txt ctzgenerator.jar -C $(OUTPUT_DIR) .

tidy:
	rm -rf $(OUTPUT_DIR)
	rm manifest.txt

clean:
	rm -rf $(OUTPUT_DIR)
	rm ctzgenerator.jar
	rm manifest.txt
