package vu.cltl.storyteller.knowledgestore;

/**
 * Created by piek on 11/12/2016.
 */
public class KnowledgeStoreQueryApi {

    static boolean STRICTSTRING = true;
    static String KSLIMIT = "2000";
    static String log = "";

    static public void main (String[] args) {
        String query = "";
        if (args.length==0) {
            args = new String[]{"--entityPhrase", "bank;money", "--entityType", "dbp:Bank", "--entityInstance", "dbpedia:Rabo",
                                "eventPhrase", "kill", "eventType", "eso:Killing", "--topic", "eurovoc:16789", "--grasp", "POSITIVE"};
        }
        query = createSparqlQuery(args);
        System.out.print(query);
        //System.out.println("query = " + query);
        //System.out.println("log = " + log);
    }

    static public String createSparqlQuery (String [] args) {
        String eventPhrase = "";
        String eventType = "";
        String entityPhrase = "";
        String entityType = "";
        String entityInstance = "";
        String word = "";
        String topicQuery = "";
        String graspQuery = "";
        String authorPhrase = "";
        String citePhrase = "";
        String authorType = "";
        String citeType = "";
        String yearBegin = "";
        String yearEnd = "";
        String locationPhrase = "";
        String locationRegion = "";

        String sparql = SparqlGenerator.makeSparqlQueryInit();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("--word") && args.length>(i+1)) {
                word = args[i+1];
                log += " -- querying for word = " + word;
            }
            else if (arg.equalsIgnoreCase("--eventPhrase") && args.length>(i+1)) {
                eventType = args[i+1];
                log += " -- querying for eventPhrase = " + eventPhrase;
            }
            else if (arg.equalsIgnoreCase("--eventType") && args.length>(i+1)) {
                eventType = args[i+1];
                log += " -- querying for eventType = " + eventType;
            }
            else if (arg.equalsIgnoreCase("--entityPhrase") && args.length>(i+1)) {
                entityPhrase = args[i+1];
                log += " -- querying for entityPhrase = " + entityPhrase;
            }
            else if (arg.equalsIgnoreCase("--entityType") && args.length>(i+1)) {
                entityType = args[i+1];
                log += " -- querying for entityType = " + entityType;
            }
            else if (arg.equalsIgnoreCase("--entityInstance") && args.length>(i+1)) {
                entityInstance = args[i+1];
                log += " -- querying for entityInstance = " + entityInstance;
            }
            else if (arg.equalsIgnoreCase("--authorPhrase") && args.length>(i+1)) {
                authorPhrase = args[i+1];
                log += " -- querying for authorPhrase = " + authorPhrase;
            }
            else if (arg.equalsIgnoreCase("--authorType") && args.length>(i+1)) {
                authorType = args[i+1];
                log += " -- querying for authorType = " + authorType;
            }
            else if (arg.equalsIgnoreCase("--citePhrase") && args.length>(i+1)) {
                citePhrase = args[i+1];
                log += " -- querying for citedPhrase = " + citePhrase;
            }
            else if (arg.equalsIgnoreCase("--citeType") && args.length>(i+1)) {
                citeType = args[i+1];
                log += " -- querying for citeType = " + citeType;
            }
            else if (arg.equalsIgnoreCase("--grasp") && args.length>(i+1)) {
                graspQuery = args[i+1];
                log += " -- querying for grasp = " + graspQuery;
            }
            else if (arg.equalsIgnoreCase("--topic") && args.length>(i+1)) {
                topicQuery = args[i+1];
                log += " -- querying for topic = " + topicQuery;
            }
            else if (arg.equalsIgnoreCase("--substring")) {
                STRICTSTRING = false;
                log += " -- querying for substrings.";
            }
            else if (arg.equalsIgnoreCase("--ks-limit") && args.length>(i+1)) {
                KSLIMIT = args[i+1];
                SparqlGenerator.limit = KSLIMIT;
                log += " -- limit = " +KSLIMIT;
            }
        }



        if (!word.isEmpty()) {
            eventPhrase = word;
            entityPhrase = word;
        }

        // Events
        if (!eventPhrase.isEmpty() || !eventType.isEmpty()) {
            sparql += "{";

            if (!eventPhrase.isEmpty()) {
                String labels = SparqlGenerator.getLabelQueryforEvent(entityPhrase);
                if (STRICTSTRING) sparql += SparqlGenerator.makeLabelConstraint("?event", labels);
                else {sparql += SparqlGenerator.makeSubStringLabelFilter("?event", labels); }
            }
            if (!eventType.isEmpty()) {
                String types = SparqlGenerator.getTypeQueryforEvent(eventType);
                if (!eventPhrase.isEmpty()) {
                    sparql += " UNION ";
                }
                sparql += SparqlGenerator.makeTypeFilter("?event", types);
            }
            sparql += "}";
        }


        //Entities
        if (!entityPhrase.isEmpty() || !entityType.isEmpty() || !entityInstance.isEmpty()) {
            //split query into types, instances and labels
            //
            String labels = SparqlGenerator.getLabelQueryforEntity(entityPhrase);
            String types = SparqlGenerator.getTypeQueryforEntity(entityType);
            String instances = SparqlGenerator.getInstanceQueryforEntity(entityInstance);
            sparql += "?event sem:hasActor ?ent .\n";
            sparql += "{";
            if (!labels.isEmpty()) {
                //makeLabelFilter("?entlabel",entityLabel) +
                if (labels.indexOf("*")>-1)  {
                    labels = labels.replace("*", "");
                    sparql += SparqlGenerator.makeSubStringLabelFilter("?entlabel", labels);
                    sparql += "?ent rdfs:label ?entlabel .\n" ;
                }
                else if (STRICTSTRING) {
                    sparql += SparqlGenerator.makeLabelConstraint("?ent", labels);
                }
                else {
                    sparql += SparqlGenerator.makeSubStringLabelFilter("?entlabel", labels);
                    sparql += "?ent rdfs:label ?entlabel .\n" ;
                }
            }
            if (!instances.isEmpty()) {
                if (!labels.isEmpty()) {
                    sparql += " UNION ";
                }
                sparql += SparqlGenerator.makeInstanceFilter("?event", instances);
                // "?event sem:hasActor ?ent .";

            }
            if (!types.isEmpty()) {
                if (!labels.isEmpty() || !instances.isEmpty()) {
                    sparql += " UNION ";
                }
                sparql += SparqlGenerator.makeTypeFilter("?ent", types) ;
            }
            sparql += "}";
        }

        if (!topicQuery.isEmpty()) {
            sparql += SparqlGenerator.makeTopicFilter("?event", topicQuery);
        }

         /*
          @TODO implement period filter for events
         */
        if (!yearBegin.isEmpty()) {
/*
            sparql += SparqlGenerator.makeYearFilter("?time", yearBegin) +
                    "?ent rdfs:label ?entlabel .\n" +
                    "?event sem:hasTime ?time .\n";
*/
        }

        /*
        <https://twitter.com/139786938/status/529378953065422848>
        prov:wasAttributedTo  nwrauthor:Twitter .

        <https://twitter.com/139786938/status/529378953065422848/source_attribution/Attr10>
        rdf:value              grasp:CERTAIN_NON_FUTURE_POS , grasp:positive ;
        grasp:wasAttributedTo  <http://www.newsreader-project.eu/data/Dasym-Pilot/non-entities/hij> .
         */
        if (!authorPhrase.isEmpty()) {
            String sources = SparqlGenerator.getSource(authorPhrase);
            if (!sources.isEmpty()) {
                sparql +=
                        "?event gaf:denotedBy ?mention.\n" +
                                "?mention grasp:hasAttribution ?attribution.\n" +
                                "?attribution prov:wasAttributedTo ?doc .\n" ;
                //"?doc prov:wasAttributedTo ?author .\n";
/*
                if (STRICTSTRING) sparql += TrigKSTripleReader.makeLabelConstraint("?author", sources);
                else {sparql += TrigKSTripleReader.makeSubStringLabelFilter("?author", sources); }
*/

                //sparql += TrigKSTripleReader.makeSubStringLabelFilter("?author", sources);
                sparql += SparqlGenerator.makeAuthorConstraint("?doc", sources);
            }
        }

        if (!citePhrase.isEmpty()) {
            String sources = SparqlGenerator.getSource(citePhrase);
            if (!sources.isEmpty()) {
                sparql +=
                        "?event gaf:denotedBy ?mention.\n" +
                                "?mention grasp:hasAttribution ?attribution.\n" +
                                "?attribution grasp:wasAttributedTo ?cite.\n";
/*
                if (STRICTSTRING) sparql += TrigKSTripleReader.makeLabelConstraint("?cite", sources);
                else {sparql += TrigKSTripleReader.makeSubStringLabelFilter("?cite", sources); }
*/

                sparql += SparqlGenerator.makeSubStringLabelFilter("?cite", sources);

            }
        }

            /// rdf:value grasp:CERTAIN_NON_FUTURE_POS , grasp:positive ;
            ///graspQuery = NEG;UNCERTAIN;positive;
            if (!graspQuery.isEmpty()) {
            boolean UNION = false;
            sparql += "?event gaf:denotedBy ?mention.\n" +
                    "?mention grasp:hasAttribution ?attribution.\n" +
                    "?attribution rdf:value ?value .\n" ;
            sparql += "{\n";
            if (graspQuery.indexOf("negative") >-1) {
                sparql +=  "{ ?attribution rdf:value grasp:negative } \n";
                UNION = true;
            }
            if (graspQuery.indexOf("positive") >-1) {
                if (UNION) sparql += " UNION ";
                sparql +=  "{ ?attribution rdf:value grasp:positive }\n";
                UNION = true;
            }
            String [] fields = graspQuery.split(";");
            for (int i = 0; i < fields.length; i++) {
                String field = fields[i];
                if (!field.toLowerCase().equals(field)) {
                    ///upper case field
                    if (UNION) sparql += " UNION ";
                    sparql +=  "{ "+ SparqlGenerator.makeSubStringLabelUnionFilter("?value", field) +" }"+ "\n";
                    UNION = true;
                }
            }
            sparql += " }\n";
            if (graspQuery.indexOf("FUTURE") > -1) {
                sparql += "FILTER(!CONTAINS(STR(?value), \"NON_FUTURE\"))\n";
            }
        }

        sparql += SparqlGenerator.makeSparqlQueryEnd();

        return sparql;
    }


}