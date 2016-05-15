1. Create a Bluemix Account

   [Sign up][sign_up] in Bluemix or use an existing account. Watson Services
   in Beta are free to use.

2. Download and install the [Cloud-foundry CLI][cloud_foundry] tool.

3. Edit the `manifest.yml` file and change the `<application-name>` to something unique.

  ```none
  applications:
  - services:
    - retrieve-and-rank-service
    name: <application-name>
    path: webApp.war
    memory: 512M
  ```

  The name you use determines your initial application URL, e.g.,
  `<application-name>.mybluemix.net`.

4. Connect to Bluemix in the command line tool.

  ```sh
  $ cf api https://api.ng.bluemix.net
  $ cf login -u <your-user-ID>
  ```

5. Create the Retrieve and Rank service in Bluemix.

6. Download and install the [maven][maven] compiler.

7. Build the project.

   You need to use the Apache `maven` to build the war file.

  ```sh
  $ maven install
  ```

8. Push it live!

  ```sh
  $ cf push -p target/webApp.war
  ```

9. Train the service, See a tutorial in <a href="http://www.ibm.com/smarterplanet/us/en/ibmwatson/developercloud/doc/retrieve-rank/get_start.shtml" target="_blank"> Getting started with the Retrieve and Rank service</a>. As you complete the tutorial, save this information:
  * Solr cluster ID: The unique identifier of the Apache Solr Cluster that you create.
  * Collection name: The name you give to the Solr collection when you create it.  
  * Ranker ID: The unique identifier of the ranker you create.

10. Use the values from the tutorial to specify environment variables in your app.  

  1. Navigate to the application dashboard in Bluemix.
  2. Click the Retrieve and Rank application you created earlier.
  3. Click **Environment Variables**.
  4. Click **USER-DEFINED**.
  5. Add the following three environment variables with the values that you copied from the tutorial:
      * `CLUSTER_ID`
      * `COLLECTION_NAME`
      * `RANKER_ID`
