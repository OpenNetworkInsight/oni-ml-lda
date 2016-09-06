package org.opennetworkinsight.dns
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.functions._

class DNSScoreFunction(topicCount: Int,
                         ipToTopicMixBC: Broadcast[Map[String, Array[Double]]],
                         wordToPerTopicProbBC: Broadcast[Map[String, Array[Double]]]) extends Serializable {

  def score(ip: String, word: String): Double = {

    val uniformProb = Array.fill(topicCount) {
      1.0d / topicCount
    }

    val topicGivenDocProbs = ipToTopicMixBC.value.getOrElse(ip, uniformProb)
    val wordGivenTopicProbs = wordToPerTopicProbBC.value.getOrElse(word, uniformProb)

    topicGivenDocProbs.zip(wordGivenTopicProbs)
      .map({ case (pWordGivenTopic, pTopicGivenDoc) => pWordGivenTopic * pTopicGivenDoc })
      .sum
  }

}
