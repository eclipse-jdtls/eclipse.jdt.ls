
package org.jboss.tools.langs;

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class InitializeParams {

    /**
     * The process Id of the parent process that started
     * 
     * the server.
     * 
     */
    @SerializedName("processId")
    @Expose
    private Double processId;
    /**
     * The rootPath of the workspace. Is null
     * 
     * if no folder is open.
     * 
     */
    @SerializedName("rootPath")
    @Expose
    private String rootPath;
    /**
     * Defines the capabilities provided by the client.
     * 
     */
    @SerializedName("capabilities")
    @Expose
    private Object capabilities;
    /**
     * User provided initialization options.
     * 
     */
    @SerializedName("initializationOptions")
    @Expose
    private Object initializationOptions;

    /**
     * The process Id of the parent process that started
     * 
     * the server.
     * 
     * @return
     *     The processId
     */
    public Double getProcessId() {
        return processId;
    }

    /**
     * The process Id of the parent process that started
     * 
     * the server.
     * 
     * @param processId
     *     The processId
     */
    public void setProcessId(Double processId) {
        this.processId = processId;
    }

    public InitializeParams withProcessId(Double processId) {
        this.processId = processId;
        return this;
    }

    /**
     * The rootPath of the workspace. Is null
     * 
     * if no folder is open.
     * 
     * @return
     *     The rootPath
     */
    public String getRootPath() {
        return rootPath;
    }

    /**
     * The rootPath of the workspace. Is null
     * 
     * if no folder is open.
     * 
     * @param rootPath
     *     The rootPath
     */
    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public InitializeParams withRootPath(String rootPath) {
        this.rootPath = rootPath;
        return this;
    }

    /**
     * Defines the capabilities provided by the client.
     * 
     * @return
     *     The capabilities
     */
    public Object getCapabilities() {
        return capabilities;
    }

    /**
     * Defines the capabilities provided by the client.
     * 
     * @param capabilities
     *     The capabilities
     */
    public void setCapabilities(Object capabilities) {
        this.capabilities = capabilities;
    }

    public InitializeParams withCapabilities(Object capabilities) {
        this.capabilities = capabilities;
        return this;
    }

    /**
     * User provided initialization options.
     * 
     * @return
     *     The initializationOptions
     */
    public Object getInitializationOptions() {
        return initializationOptions;
    }

    /**
     * User provided initialization options.
     * 
     * @param initializationOptions
     *     The initializationOptions
     */
    public void setInitializationOptions(Object initializationOptions) {
        this.initializationOptions = initializationOptions;
    }

    public InitializeParams withInitializationOptions(Object initializationOptions) {
        this.initializationOptions = initializationOptions;
        return this;
    }

}
