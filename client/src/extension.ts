
'use strict';

import * as path from 'path';
import * as cp from 'child_process';
import * as fs from 'fs';

var electron = require('./electron_j');
var rimraf = require('rimraf');

import { workspace, Disposable, ExtensionContext } from 'vscode';
import { LanguageClient, LanguageClientOptions, SettingMonitor, ServerOptions } from 'vscode-languageclient';

declare var v8debug;
var DEBUG =( typeof v8debug === 'object');

function runJavaServer(){
	return new Promise(function(resolve, reject){
			let child = 'java';
			let params = [];
			let workspacePath = path.resolve( __dirname,"../../server/vscodesws_"+makeRandomHexString(5));
			if(DEBUG){
				params.push('-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044');
				// suspend=y is the default. Use this form if you need to debug the server startup code:
				// params.push('-agentlib:jdwp=transport=dt_socket,server=y,address=1044');
			}
			params.push('-Declipse.application=org.jboss.tools.vscode.java.id1');
			params.push('-Dosgi.bundles.defaultStartLevel=4');
			params.push('-Declipse.product=org.jboss.tools.vscode.java.product');
			if(DEBUG)
				params.push('-Dlog.protocol=true');
		
			params.push('-jar'); params.push(path.resolve( __dirname ,'../../server/plugins/org.eclipse.equinox.launcher_1.3.200.v20160318-1642.jar'));
			//select configuration directory according to OS
			let configDir = 'config_win';
			if ( process.platform === 'darwin' ){
				configDir = 'config_mac';
			}else if(process.platform === 'linux'){
				configDir = 'config_linux';
			}
			params.push('-configuration');params.push(path.resolve( __dirname ,'../../server',configDir));
			params.push('-data'); params.push(workspacePath);
			
			electron.fork(child,params,{}, function(err, result){
				if(err) reject(err);
				if(result) resolve(result);
			});
	});
}

export function activate(context: ExtensionContext) {

	//clean old workspaces 
	//TODO: run this during shutdown.
	cleanWorkspaces();

	// If the extension is launch in debug mode the debug server options are use
	// Otherwise the run options are used
	let serverOptions= runJavaServer;
	
	
	// Options to control the language client
	let clientOptions: LanguageClientOptions = {
		// Register the server for java
		documentSelector: ['java'],
		synchronize: {
			// Synchronize the setting section 'languageServerExample' to the server
			configurationSection: 'languageServerExample',
			// Notify the server about file changes to '.clientrc files contain in the workspace
			fileEvents: workspace.createFileSystemWatcher('**/*.java')
		}
	}
	
	// Create the language client and start the client.
	let disposable = new LanguageClient('Java Language Support', serverOptions, clientOptions).start();
	
	// Push the disposable to the context's subscriptions so that the 
	// client can be deactivated on extension deactivation
	context.subscriptions.push(disposable);
}

function makeRandomHexString(length) {
    var chars = ['0', '1', '2', '3', '4', '5', '6', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'];
    var result = '';
    for (var i = 0; i < length; i++) {
        var idx = Math.floor(chars.length * Math.random());
        result += chars[idx];
    }
    return result;
}

function cleanWorkspaces(){
	let serverDir = path.resolve( __dirname,"../../server/");
	
	fs.readdir(serverDir, function (err, files){
		if(err) return;
		files.forEach(function(item,index, array){
			if(item.startsWith('vscodesws_')){
				rimraf.sync(path.resolve(serverDir, item));
			}
		});
	});
}