/*
 * Copyright 2018
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.conceptlinking.ranking.letor;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tudarmstadt.ukp.inception.kb.graph.KBHandle;

public class PredictionRequest {

    private String mention;
    private String context;
    private List<KBHandle> candidates;

    public PredictionRequest(String aMention, String aContext, List<KBHandle> aCandidates)
    {
        mention = aMention;
        context = aContext;
        candidates = aCandidates;
    }

    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();

        result.put("mention", mention);
        result.put("context", context);

        ArrayNode candidatesNode = result.putArray("candidates");

        for (KBHandle candidate : candidates) {
            ObjectNode candidateNode = mapper.createObjectNode();

            candidateNode.put("iri", candidate.getIdentifier());
            candidateNode.put("label", candidate.getName());
            candidateNode.put("description", candidate.getDescription());
            candidatesNode.add(candidateNode);
        }

        return result.toPrettyString();
    }
}