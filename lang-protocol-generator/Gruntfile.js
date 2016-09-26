
var TJS = require('typescript-json-schema').TJS;

/*global module:false*/
module.exports = function (grunt) {

  // Project configuration.
  grunt.initConfig({
    // Task configuration.
    shell: {
       gitclone:{
            command: [
               'mkdir ./nodeClient',
               'cd ./nodeClient',
               'git clone git@github.com:Microsoft/vscode-languageserver-node.git',
               'cd vscode-languageserver-node/client',
               'npm install .'
            ].join('&&')
        },
    }
  });

  // These plugins provide necessary tasks.
  grunt.loadNpmTasks('grunt-contrib-watch');
  grunt.loadNpmTasks('grunt-shell');  

  grunt.registerTask('copyTypes','copy types to be generated', function(){
      grunt.file.delete('./nodeClient/vscode-languageserver-node/client/src/generatedTypes.ts');
      grunt.file.copy('./generatedTypes.ts' ,'./nodeClient/vscode-languageserver-node/client/src/generatedTypes.ts');
  });

  grunt.registerTask('generate_schema', 'Generate schema from .ts', function () {
      var filename = './ls-protocol/types.json'
      createJSONSchema('AllTypes', filename);
      var filename2 = './ls-protocol/messages.json'
      createJSONSchema('AllMessages', filename2);
      var filename3 = './ls-protocol/everything.json'
      createJSONSchema('AllProtocol', filename3);
  });
  grunt.registerTask('generate', ['copyTypes','generate_schema']);

var program = TJS.programFromConfig('./nodeClient/vscode-languageserver-node/client/src/tsconfig.json');
function createJSONSchema(element, destFile) {
  var args = {
    useRef: true,
    useTypeAliasRef: false,
    useRootRef: false,
    useTitle: false,
    useDefaultProperties: false,
    disableExtraProperties: true,
    usePropertyOrder: false,
    generateRequired: true,
    out: undefined
  };
  args.out = destFile;
  var definition = TJS.generateSchema(program, element, args);
  grunt.file.write(destFile,JSON.stringify(definition, null, 4) + "\n" );
}
};
