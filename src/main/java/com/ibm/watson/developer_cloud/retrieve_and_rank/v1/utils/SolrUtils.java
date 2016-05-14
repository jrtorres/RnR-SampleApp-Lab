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
package com.ibm.watson.developer_cloud.retrieve_and_rank.v1.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.ModifiableSolrParams;

import com.google.gson.JsonObject;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.RankResult;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.Ranker;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.SolrResult;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.model.SolrResults;
import com.ibm.watson.developer_cloud.retrieve_and_rank.v1.payload.QueryRequestPayload;


/**
 * Utility class to search in for documents Solr using or not a {@link Ranker}
 */
public class SolrUtils {

  private static final String TITLE = "title";
  private static final String ID = "id";
  private static final String BODY = "text";//"body";
  private static String FCSELECT_REQUEST_HANDLER = "/fcselect";
  private static String FEATURE_VECTOR_FIELD = "featureVector";
  private static String FIELD_LIST_PARAM = "fl";
  private JsonObject groundTruth;
  private static String ID_FIELD = ID;
  private static Logger logger = Logger.getLogger(SolrUtils.class.getName());
  private static String RANKER_ID = "ranker_id";
  private static String SCORE_FIELD = "score";
  private String collectionName;
  private String rankerId;
  private HttpSolrClient solrClient;

  /**
   *
   * @param solrClient the Solr client
   * @param groundTruth the ground truth
   * @param collectionName the collection name
   * @param rankerID the ranker id
   */
  public SolrUtils(HttpSolrClient solrClient, JsonObject groundTruth, String collectionName,
      String rankerID) {
    this.rankerId = rankerID;
    this.collectionName = collectionName;
    this.solrClient = solrClient;
    this.groundTruth = groundTruth;    
  }

  /**
   * Process a Solr request up to 3 times.
   *
   * @param request the request
   * @return the query response
   * @throws IOException Signals that an I/O exception has occurred.
   * @throws SolrServerException the Solr server exception
   * @throws InterruptedException the interrupted exception
   */
  private QueryResponse processSolrRequest(QueryRequest request)
      throws IOException, SolrServerException, InterruptedException {
    int currentAttempt = 0;
    QueryResponse response;
    while (true) {
      try {    	  
    	currentAttempt++;
        response = request.process(solrClient, collectionName);
        break;
      } catch (Exception e) {
        if (currentAttempt < 3) {
          Thread.sleep(1000);
        } else {
          throw e;
        }
      }
    }
    return response;
  }

  /**
   * Search Sorl using a {@link Ranker} if <code>useRanker</code> is specified
   *
   * @param query the query
   * @param useRanker the use ranker
   * @return the list
   */
  public SolrResults search(QueryRequestPayload query, boolean useRanker) {

    try {
      logger.info("Searching for document...");
      QueryResponse queryResponse = solrRuntimeQuery(query.getQuery(), useRanker);
      
      logger.info("Found " + queryResponse.getResults().size() + " documents!");

      List<RankResult> answerList = new ArrayList<>();
      List<String> ids = new ArrayList<>();

      int i = 0;
      Iterator<SolrDocument> it = queryResponse.getResults().iterator();
      while (it.hasNext()) {
        SolrDocument doc = it.next();
        ids.add((String) doc.getFieldValue(ID_FIELD));
        String score = String.valueOf(doc.getFieldValue(SCORE_FIELD));
        if (i++ < 10) {

          RankResult a = new RankResult();
          a.setAnswerId((String) doc.getFieldValue(ID_FIELD));
          a.setScore(Float.parseFloat(score));
          a.setFinalRank(i);

          if (query.getId() != -1 && groundTruth != null) {
            // If it is a canned query, get ground truth info
            String id = String.valueOf(query.getId());
            if (groundTruth.has(id)) {
              JsonObject gtForQuery = groundTruth.get(id).getAsJsonObject();
              if (gtForQuery.has(a.getAnswerId())) {
                a.setRelevance(gtForQuery.get(a.getAnswerId()).getAsInt());
              } else if (query.getId() != -1) {
                a.setRelevance(0);
              }
            }
          }
          answerList.add(a);
        }

      }

      SolrResults result = new SolrResults();
      result.setNumberOfResults(queryResponse.getResults().size());
      result.setIds(ids);
      result.setResult(answerList);
      return result;
    } catch (IOException | SolrServerException | InterruptedException e) {
      logger.log(Level.SEVERE, "Error searching Solr", e);
    }
    return null;
  }


  /**
   * Create and process a Solr query
   *
   * @param query the query
   * @param featureVector the feature vector
   * @return the query response
   * @throws IOException Signals that an I/O exception has occurred.
   * @throws SolrServerException the Solr server exception
   * @throws InterruptedException the interrupted exception
   */
  private QueryResponse solrRuntimeQuery(String query, boolean featureVector)
      throws IOException, SolrServerException, InterruptedException {
    SolrQuery featureSolrQuery = new SolrQuery(query);
    if (featureVector) {
      featureSolrQuery.setRequestHandler(FCSELECT_REQUEST_HANDLER);
      // add the ranker id - this tells the plugin to re-reank the results in a single pass
      featureSolrQuery.setParam(RANKER_ID, rankerId);

    }

    // bring back the id, score, and featureVector for the feature query
    featureSolrQuery.setParam(FIELD_LIST_PARAM, ID_FIELD, SCORE_FIELD, FEATURE_VECTOR_FIELD);

    // need to ask for enough rows to ensure the correct answer is included in the resultset
    featureSolrQuery.setRows(1000);    
    QueryRequest featureRequest = new QueryRequest(featureSolrQuery, METHOD.POST);
    
    return processSolrRequest(featureRequest);
  }

  /**
   * Gets the documents by ids.
   *
   * @param idsToRetrieve the ids of documents to retrieve
   * @return the documents
   */
  public Map<String, SolrResult> getDocumentsByIds(ArrayList<String> idsToRetrieve) {
    SolrDocumentList docs;
    Map<String, SolrResult> idsToDocs = new HashMap<>();
    try {
      
      docs = solrClient.getById(collectionName, idsToRetrieve, new ModifiableSolrParams());

      for (SolrDocument doc : docs) {
        SolrResult result = new SolrResult();
        result.setBody(doc.getFirstValue(BODY).toString().replaceAll("\\s+", " ").trim());
        result.setId(doc.getFirstValue(ID).toString());
        result.setTitle(doc.getFirstValue(TITLE).toString().replaceAll("\\s+", " ").trim());
        idsToDocs.put(result.getId(), result);
      }
    } catch (IOException | SolrServerException e) {
      logger.log(Level.SEVERE, "Error retrieven the Solr documents", e);
    }
    return idsToDocs;
  }


}
