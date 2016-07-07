
package org.jboss.tools.langs;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * Represents a reference to a command. Provides a title which will be used to represent a command in the UI and, optionally, an array of arguments which will be passed to the command handler function when invoked.
 * 
 */
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
     * Arguments that the command handler should be invoked with.
     * 
     */
    @SerializedName("arguments")
    @Expose
    private Set<Object> arguments = new LinkedHashSet<Object>();

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

    /**
     * Arguments that the command handler should be invoked with.
     * 
     * @return
     *     The arguments
     */
    public Set<Object> getArguments() {
        return arguments;
    }

    /**
     * Arguments that the command handler should be invoked with.
     * 
     * @param arguments
     *     The arguments
     */
    public void setArguments(Set<Object> arguments) {
        this.arguments = arguments;
    }

}
