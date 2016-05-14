/**
 * Copyright 2015 IBM Corp. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ibm.watson.developer_cloud.retrieve_and_rank.v1;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Base64;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.RankResult;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.SolrResult;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.SolrResults;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.payload.QueryRequestPayload;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.payload.QueryResponsePayload;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.utils.HttpSolrClientUtils;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.utils.SolrUtils;
import com.ibm.watson.developer_cloud.service.ServiceResponseException;
import com.ibm.watson.developer_cloud.util.CredentialUtils;


/**
 * Sample application for using the IBM Watson Retrieve and Rank V1 service.
 */

@Path("/")
public class RetrieveAndRankResource {

  private static final String BASIC = "Basic ";
  private static final String RETRIEVE_AND_RANK = "retrieve_and_rank";
  private static Logger logger = Logger.getLogger(RetrieveAndRankResource.class.getName());

  private RetrieveAndRank service;
  private SolrUtils solrUtils;

  /**
   * Instantiates a new retrieve and rank resource.
   *
   * @throws FileNotFoundException ground truth file not found
   */
  public RetrieveAndRankResource() {
    
    // When running locally you need to provide values for the variables below
    // WHen running in Bluemix the values will be provided in the environment variables
    String clusterId = System.getenv("CLUSTER_ID");
    String rankerId =  System.getenv("RANKER_ID");
    String collectionName = System.getenv("COLLECTION_NAME");
    String username = "USERNAME";
    String password = "PASSWORD";   
    String endPoint = "https://gateway.watsonplatform.net/retrieve-and-rank/api";

    
    // Get username and password from the VCAP_SERVICES environment variable in Bluemix
    String apiKey = CredentialUtils.getAPIKey(RETRIEVE_AND_RANK);
        
    if (apiKey != null) {
      // credentials = username:password
      String[] credentials = new String(Base64.decodeBase64(apiKey.replaceAll(BASIC,""))).split(":");
      username = credentials[0];
      password = credentials[1];
    }
    
    
    // Service instance
    this.service = new RetrieveAndRank();
    service.setUsernameAndPassword(username, password);
    
    // Ground truth
    InputStreamReader reader =
        new InputStreamReader(getClass().getResourceAsStream("/groundtruth.json"));
    JsonObject groundTruth = new JsonParser().parse(reader).getAsJsonObject();
    
    // Solr Client
    HttpSolrClient solrClient = new HttpSolrClient(service.getSolrUrl(clusterId),
        HttpSolrClientUtils.createHttpClient(endPoint, username, password));

    solrUtils = new SolrUtils(solrClient, groundTruth, collectionName, rankerId);

    if (clusterId == null || collectionName ==null || rankerId == null)
      logger.warning("CLUSTER_ID, RANKER_ID and COLLECTION_NAME cannot be null");
    
    logger.info("RetrieveAndRank service initialized");
  }

  /**
   * Ping.
   *
   * @return the string
   */
  @GET
  @Path("/ping")
  public String ping() {
    return "pong";
  }


  /**
   * Performs a query against the Solr and then makes a call to rank the results. The order of the
   * results after both calls is recorded and the returned results are noted in each payload. Once
   * the ranked results are retrieved a third API call is made to the Solr retrieve service to
   * retrieve the body (text) for each result. A lookup is performed to get the ground truth
   * relevance value for each returned result. This lookup would not normally be performed, but as a
   * goal of this sample application is to show the user how training affects the final results of
   * the ranker, we return that info also.
   *
   * @param body the user query
   * @return the string
   * @throws InterruptedException
   * @throws SolrServerException
   * @throws IOException
   */
  @POST
  @Path("/query")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response query(QueryRequestPayload body) {
    try {
      QueryResponsePayload queryResponse = new QueryResponsePayload();
      queryResponse.setQuery(body.getQuery());

      SolrResults rankedResults = solrUtils.search(body, true);
      queryResponse.setRankedResults(rankedResults.getResult());
      
      SolrResults solrResults = solrUtils.search(body, false);
      queryResponse.setSolrResults(solrResults.getResult());
      queryResponse.setNumSolrResults(solrResults.getNumberOfResults());


      // 1. Collects all the documents ids to retrieve the title and body in a single query
      ArrayList<String> idsOfDocsToRetrieve = new ArrayList<>();

      for (RankResult answer : queryResponse.getRankedResults()) {
        idsOfDocsToRetrieve.add(answer.getAnswerId());
        answer.setSolrRank(solrResults.getIds().indexOf(answer.getAnswerId()));
      }
      for (RankResult answer : queryResponse.getSolrResults()) {
        idsOfDocsToRetrieve.add(answer.getAnswerId());
        answer.setFinalRank(rankedResults.getIds().indexOf(answer.getAnswerId()));
      }
      
      // 2. Query Solr to retrieve document title and body
      Map<String, SolrResult> idsToDocs = solrUtils.getDocumentsByIds(idsOfDocsToRetrieve);


      // 3. Update the queryResponse with the body and title
      for (RankResult answer : queryResponse.getRankedResults()) {
        answer.setBody(idsToDocs.get(answer.getAnswerId()).getBody());
        answer.setTitle(idsToDocs.get(answer.getAnswerId()).getTitle());
      }
      for (RankResult answer : queryResponse.getSolrResults()) {
        answer.setBody(idsToDocs.get(answer.getAnswerId()).getBody());
        answer.setTitle(idsToDocs.get(answer.getAnswerId()).getTitle());
      }

      return Response.ok(queryResponse).build();
    } catch (ServiceResponseException e) {
      return Response.status(e.getStatusCode())
          .entity(createError(e.getStatusCode(),e.getMessage()))
          .build();
    } catch (Exception e) {
      return Response.status(500)
          .entity(createError(500, "Internal Server Error. Did you create and train the service?"))
          .build();
    }
  }


  /**
   * Creates the JSON error based on the service exception.
   *
   * @param e the {@link ServiceResponseException}
   * @return the JSON error as string
   */
  private String createError(int statusCode, String message) {
    JsonObject error = new JsonObject();
    error.addProperty("code", statusCode);
    error.addProperty("error", message);
    return error.toString();
  }

}
