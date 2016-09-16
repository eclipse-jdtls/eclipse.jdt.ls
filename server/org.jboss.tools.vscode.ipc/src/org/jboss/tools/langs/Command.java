
package org.jboss.tools.langs;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class Command {

    /**
     * Title of the command, like `save`.
     * 
     */
    @SerializedName("title")
    @Expose
    private String title;
    /**
     * The identifier of the actual command handler.
     * 
     */
    @SerializedName("command")
    @Expose
    private String command;
    /**
     * Arguments that the command handler should be
     * 
     * invoked with.
     * 
     */
    @SerializedName("arguments")
    @Expose
    private List<Object> arguments = new ArrayList<Object>();

    /**
     * Title of the command, like `save`.
     * 
     * @return
     *     The title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Title of the command, like `save`.
     * 
     * @param title
     *     The title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    public Command withTitle(String title) {
        this.title = title;
        return this;
    }

    /**
     * The identifier of the actual command handler.
     * 
     * @return
     *     The command
     */
    public String getCommand() {
        return command;
    }

    /**
     * The identifier of the actual command handler.
     * 
     * @param command
     *     The command
     */
    public void setCommand(String command) {
        this.command = command;
    }

    public Command withCommand(String command) {
        this.command = command;
        return this;
    }

    /**
     * Arguments that the command handler should be
     * 
     * invoked with.
     * 
     * @return
     *     The arguments
     */
    public List<Object> getArguments() {
        return arguments;
    }

    /**
     * Arguments that the command handler should be
     * 
     * invoked with.
     * 
     * @param arguments
     *     The arguments
     */
    public void setArguments(List<Object> arguments) {
        this.arguments = arguments;
    }

    public Command withArguments(List<Object> arguments) {
        this.arguments = arguments;
        return this;
    }

}
