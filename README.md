# Retrieve and Rank Demo
The IBM Watson [Retrieve and Rank][service_url] service helps users find the most relevant information for their queries by using a combination of search and machine learning algorithms to detect "signals" in the data. You load your data into the service, which is built on top of Apache Solr, and train a machine learning model. Then use the trained model to provide improved results to users.

## Before you begin
Ensure that you have the following prerequisites before you start:

* A Bluemix account. If you don't have one, [sign up][sign_up]
* [Java Development Kit](http://www.oracle.com/technetwork/java/javase/downloads/index.html) version 1.7 or later
* [Eclipse IDE for Java EE Developers](https://www.eclipse.org/downloads/packages/eclipse-ide-java-ee-developers/marsr)
* [Apache Maven](https://maven.apache.org/download.cgi), version 3.1 or later
* [Git](https://git-scm.com/downloads)
* [Websphere Liberty Profile server](https://developer.ibm.com/wasdev/downloads/liberty-profile-using-non-eclipse-environments/), if you want to run the app in your local environment


## Set up Retrieve and Rank

### Provision A Retrieve and Rank Service

1. You need a Bluemix account. If you don't have one, [sign up][sign_up].

1. Download and install the [Cloud-foundry CLI][cloud_foundry] tool if you haven't already.

1. Provision an R&R service by going through the Bluemix catalog. Or using the CF command line tools.

	1. [CF Option] Connect to Bluemix with the command line tool.
  		```sh
  		$ cf api https://api.ng.bluemix.net
  		$ cf login -u <your user ID>
  		```

	1. [CF Option] Create the Retrieve and Rank service.
  		```sh
  		$ cf create-service retrieve_and_rank standard retrieve-and-rank-ws1
  		```

### Configure the Retrieve and Rank Service

1. Gather the credentials for the provisioned R&R service through the service dashboard in bluemix or the following CF commands:
	
	1. Get Service name:
		```sh
		$ cf services
		```
  		
	1. Copy the service name to get the Service Keys:
		```sh
		$ cf service-keys "{SERVICE_NAME}"
		```
  		
	1. Copy the service key name to get credentials:
		```sh
  		$ cf service-key "{SERVICE_NAME}" "{SERVICE_KEY_NAME}"
  		```

	1. Copy the username and password.

1. Create the Solr cluster using the following curl command
	```sh
	curl -H “Content-Type: application/json” -X POST -u "{credentials_username}":"{credentials_password}" -d “{\”cluster_size\”:\”1\”,\”cluster_name\”:\”ws_niddk_cluster\”}” “https://gateway.watsonplatform.net/retrieve-and-rank/api/v1/solr_clusters"
	```

1. Take note of the `<solr_cluster_id>`.
  
1. Wait until the cluster becomes available. Use the following command to check status:
	```sh
	curl -u "{credentials_username}":"{credentials_password}" "https://gateway.watsonplatform.net/retrieve-and-rank/api/v1/solr_clusters/{solr_cluster_id}"
  	```

### Index documents

1. Inspect the corpus document json file and the sample configuration schema file

1. Add the appropriate fields to the schema.xml file:

	1. Add the fields id, source, doc_type, topic, text_description. Make sure they are of type watson_text_en.
	
	1. Add a copy field called text to aggregate key fields.
	
1. Zip the configuration files.

1. Add the new configuration to the cluster using the following curl command:
	```sh
	curl -X POST -H "Content-Type: application/zip" -u "{credentials_username}":"{credentials_password}" "https://gateway.watsonplatform.net/retrieve-and-rank/api/v1/solr_clusters/{solr_cluster_id}/config/{name_of_config}" --data-binary @/{name_of_zip_file}.zip
	```
	
1. Create the collection
	```sh
	curl -X POST -H "Content-Type: application/zip" -u "{credentials_username}":"{credentials_password}" "https://gateway.watsonplatform.net/retrieve-and-rank/api/v1/solr_clusters/{solr_cluster_id}/solr/admin/collections" -d "action=CREATE&name=ws_niddk_collection&collection.configName={name_of_config}&wt=json"
	```

1. Gather the documents already prepared in the solrdocs.json file (in corpus directory)

1. Index them in the collection:
	```sh
	curl -X POST -H "Content-Type: application/json" -u "{credentials_username}":"{credentials_password}" "https://gateway.watsonplatform.net/retrieve-and-rank/api/v1/solr_clusters/{solr_cluster_id}/solr/{name_of_collection}/update?commit=true" --data-binary @/{name_of_solrdocs_file}.json
	```

1. Validate the collection has documents by:
	1. checking the number of indexed documents with the following command:
		```sh
		curl -u "{credentials_username}":"{credentials_password}" "https://gateway.watsonplatform.net/retrieve-and-rank/api/v1/solr_clusters/{solr_cluster_id}/solr/{name_of_collection}/select?q=*:*&rows=0&wt=json"
		```
	
		1. numFound should be 1040
	
	1. Check that Solr responds to questions:
		```sh
		curl -u "{credentials_username}":"{credentials_password}" "https://gateway.watsonplatform.net/retrieve-and-rank/api/v1/solr_clusters/{solr_cluster_id}/solr/{name_of_collection}/select?q=What%20are%20the%20symptoms%20of%20Appendicitis&wt=json&fl=id,topic,text_description"
		```

### Create the Ranker

1. Inspect the ground truth relevance file (training/gt_train.csv)

1. Using the training python file, generate the training data file and train the ranker. Supplying the desired ranker name
	```sh
	python train_improved.py -u {credentials_username}:{credentials_password} -i training/gt_train.csv -c {solr_cluster_id} -x {name_of_collection} -r 30 -n {name_of_ranker} -d -v
	```
	
1. This will result in a trainingdata.txt file that is used to train the ranker

1. Take note of the <ranker_id>

1. Wait until the ranker becomes available. Use the following command to check status:
	```sh
	curl -u "{credentials_username}":"{credentials_password}" "https://gateway.watsonplatform.net/retrieve-and-rank/api/v1/rankerss/{ranker_id}"
	```
	
1. Check that the ranker responds to questions:
	```sh
	curl -u "{credentials_username}":"{credentials_password}" "https://gateway.watsonplatform.net/retrieve-and-rank/api/v1/solr_clusters/{solr_cluster_id}/solr/{name_of_collection}/fcselect?ranker_id={ranker_id}?q=What%20are%20the%20symptoms%20of%20Appendicitis&wt=json&fl=id,topic,text_description"
	```
1. Validate results and compare with Solr


## Running the demo application locally

The application uses the WebSphere Liberty profile runtime as its server, so you need to download and install the profile as part of the steps below.

1. Copy the service credentials, `CLUSTER_ID`, `COLLECTION_NAME` and `RANKER_ID` from your `retrieve-and-rank-service` service in Bluemix to `RetrieveAndRankResource.java`.  
 
1. Create a Liberty profile server in Eclipse.

1. Run Mavin Install

1. Add the application to the server.

1. Start the server.

1. Go to `http://localhost:9080/webApp` to see the running application.

## License

  This sample code is licensed under Apache 2.0.  
  Full license text is available in [LICENSE](LICENSE).


## Reference information
* Retrieve and Rank service [documentation](http://www.ibm.com/smarterplanet/us/en/ibmwatson/developercloud/doc/retrieve-rank/)
* [Configuring](http://www.ibm.com/smarterplanet/us/en/ibmwatson/developercloud/doc/retrieve-rank/configure.shtml) the Retrieve and Rank service
* Retrieve and Rank [API reference](http://www.ibm.com/smarterplanet/us/en/ibmwatson/developercloud/retrieve-and-rank/api/v1/)

[sign_up]: https://console.ng.bluemix.net/registration/
[cloud_foundry]: https://github.com/cloudfoundry/cli
[service_url]: http://www.ibm.com/smarterplanet/us/en/ibmwatson/developercloud/retrieve-and-rank.html
[sign_up]: https://console.ng.bluemix.net/registration/
[liberty]: https://developer.ibm.com/wasdev/downloads/
[liberty_mac]: http://www.stormacq.com/how-to-install-websphere-8-5-liberty-profile-on-mac/
[maven]: https://maven.apache.org/
