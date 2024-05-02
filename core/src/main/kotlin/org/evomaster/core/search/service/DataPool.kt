package org.evomaster.core.search.service

import com.google.inject.Inject
import opennlp.tools.stemmer.PorterStemmer
import org.evomaster.core.EMConfig
import org.evomaster.core.output.clustering.metrics.LevenshteinDistance

/**
 * Service to keep track of data values associated with a string key.
 * The key can be used to define links between actions, to enable smart re-use of data.
 * Keys are case insensitive and stemmed.
 *
 * Consider the REST example of:
 *
 * (1) GET     /users
 * (2) DELETE  /users/{id}
 *
 * The data regarding ids fetched in (1) could be associated with the key "userid".
 * Such data can then be read and reuse to create the id param in (2).
 *
 * In this specific context, this approach is also called "Response Dictionary".
 *
 * For the time being, we support 2 types of values: numeric and string.
 *
 * Matching a key does not need to be 100%... some level of differences can be tolerated.
 * We follow a similar algorithm as described in Section 5.2.1 of:
 *
 * "Automated black-box testing of nominal and error scenarios in RESTful APIs"
 *
 *
 */
class DataPool() {

    @Inject
    private lateinit var config: EMConfig

    @Inject
    private lateinit var randomness: Randomness


    private val pool : MutableMap<String, ArrayDeque<String>> = mutableMapOf()

    private val stemmer = PorterStemmer()

    internal constructor(_config: EMConfig, _randomness: Randomness) : this(){
        config = _config
        randomness = _randomness
    }


    fun normalize(s: String) : String{

        return stem(s.lowercase())
    }

    private fun stem(s: String) : String{
        stemmer.reset()
        return stemmer.stem(s)
    }

    fun addValue(key: String, data: String){

        val queue = pool.getOrPut(normalize(key)) { ArrayDeque<String>() }

        if(queue.contains(data)){
            return // already there
        }

        if(queue.size == config.maxSizeDataPool){
            queue.removeFirst()
        }
        queue.addLast(data)
    }


    fun extractValue(key: String, objectName: String? = null) : String?{

        if(pool.isEmpty()){
            return null
        }

        val k = normalize(key) // eg "Pets" get converted into "pet"

        //(1) first exact match
        var data = pool[k]
        if(data != null){
            return randomness.choose(data)
        }

        //(2) check exact match with object qualifier
        val fullQualifier = fullQualifier(k, objectName)
        if(fullQualifier != null){
            data = pool[fullQualifier]
            if(data != null){
                return randomness.choose(data)
            }
        }

        //(3) partial match
        val closestKey = closestKey(k)
        if(closestKey != null){
            return randomness.choose(pool[closestKey]!!)
        }

        //(4) partial match with object qualifier
        if(fullQualifier != null){
            val closestFullQualifier = closestKey(fullQualifier)
            if(closestFullQualifier != null){
                return randomness.choose(pool[closestFullQualifier]!!)
            }
        }

        //(5) check if any key is a substring
        val sub = pool.keys.firstOrNull { k.contains(it, true) }
        if(sub != null){
            return randomness.choose(pool[sub]!!)
        }

        //got nothing
        return null;
    }

    private fun fullQualifier(normalizedK : String, objectName: String?) : String?{
        if(objectName == null){
            return null
        }
        val name = normalize(objectName) // eg "Users" into "user"
        val id = name + normalizedK  // eg "userpet"
        return id
    }

    private fun closestKey(k: String): String? {

        val distance = org.apache.commons.text.similarity.LevenshteinDistance(config.thresholdDistanceForDataPool)

        val closest = pool.keys
            .map { Pair(it, distance.apply(it, k)) }
            .filter { it.second >= 0 }
            .minByOrNull { it.second }
            ?.first
        return closest
    }


}