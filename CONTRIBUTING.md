# How to contribute

Contributions are essential for keeping this language server great.
We try to keep it as easy as possible to contribute changes and we are
open to suggestions for making it even easier.
There are only a few guidelines that we need contributors to follow.

#

## Setting up the JDT Language Server
Below are the details about how to set up the JDT Language Server in different IDEs. **Notice**: You only need set up it once in any of your preferred IDE.

### A) Setting up the JDT Language Server in VS Code
1) Please install [Eclipse PDE support](https://marketplace.visualstudio.com/items?itemName=yaozheng.vscode-pde) extension in your VS Code first. The PDE extension's home page provides more usage details about _Reload Target Platform_, _Run JUnit Plug-in Test_, _Run Eclipse Application_.

2) Open VS Code on the `eclipse.jdt.ls` folder. The PDE extension will work with Java extension together to automatically load the eclipse.jdt.ls project. Check the status of the language tools on the lower right corner. It should show ready (thumbs up) as the image below.
  ![status indicator](images/statusMarker.png)

### B) Setting up the JDT Language Server in Eclipse
1) In Eclipse, import a maven project:

    ![Import Project](images/importProject.png)

    ![Import Project](images/importMavenProject.png)

    Select the `eclipse.jdt.ls` folder, then click yes/accept to all
following prompts:

    ![Import Project](images/importedMavenProject.png)

2) Now we need to use Tycho to download the dependencies,
this will get rid of the errors.

	At the top right arrow it will say `Set Target Platform`, select that and continue.

	![Import Project](images/setTargetPlatform.png)

	After it will change to `Reload Target Platform` select that:

    ![Import Project](images/reloadTargetPlatform.png)

3) Wait till the bottom right is done loading:

    ![Import Project](images/loadingTargetPlatform.png)

	once 100%:



    The errors should now be gone.

#

## Pull Requests

In order to submit contributions for review, please make sure you have signed the [Eclipse Contributor Agreement](https://www.eclipse.org/legal/ecafaq.php) (ECA) with your account.

Also, please ensure that your commit contains a `Signed-off-by` field with your name and account email in the footer. This is a confirmation that you are aware of the terms under which the contribution is being provided. This can be done with the `-s` flag of the `git commit` command.
