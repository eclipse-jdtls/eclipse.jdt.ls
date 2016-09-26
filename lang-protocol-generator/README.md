
#  Protocol Generator
This generates the language server protocol json schemas and java model from the schema.

## Steps for creating the model

1. Install the dependencies:

	```bash
	$ npm install
	```
2. Generate schemas 
	```bash
	$ grunt shell:gitclone
	$ grunt generate
	```
3. Generate Java files 
	```bash
	$ mvn verify
	```
4. After the Java file generation files needs to be moved manually from target/java-gen.
