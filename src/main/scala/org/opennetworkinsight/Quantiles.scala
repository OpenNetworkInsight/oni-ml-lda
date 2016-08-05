package org.opennetworkinsight

import org.apache.spark.rdd.RDD
import scala.math._

/**
  * Contains routines for the distributed calculation of quantiles and the empirical cumulative distribution function.
  */

object Quantiles extends Serializable {

  /**
    * Compute the empirical cumulative distribution function.
    *
    * @param data An RDD of doubles.
    * @return RDD[(Double,Double)] where each pair is of the form (value, ecdf at value)
    *         That is, each pair is a value and the fraction of the input data less-than-or-equal to the value.
    */

  def computeEcdf(data: RDD[Double]): RDD[(Double, Double)] = {
    val counts = data.map(v => (v, 1)).reduceByKey(_ + _).sortByKey().cache()

    val totalCountPerPartition: Array[Double] = 0.0 +: counts.mapPartitionsWithIndex {
      case (_, partition) => Iterator(partition.map({ case (sample, count) => count }).sum.toDouble)
    }.collect()

    val totalCount = totalCountPerPartition.sum

    val valueCountToLeftPairs : RDD[(Double, Double)] = counts.mapPartitionsWithIndex {
      case (index, partition) =>
        val precedingCount = totalCountPerPartition.take(index).sum
        val p = partition.scanLeft((0.0, precedingCount))({ case ((_, countToLeft), (value, countOfValue)) =>
          (value, countToLeft + countOfValue)})

        // first element is an extraneous zero and must be dropped
        p.drop(1)
    }
    valueCountToLeftPairs.map({case (value, countToLeftOfValue) => (value, countToLeftOfValue / totalCount)})
  }

  /**
    * Compute the quantiles for a given dataset and array of thresholds for the cumulative distribution.
    *
    * @param data      Incoming dataset.
    * @param quantiles Sorted array of doubles in the range from 0.0 to 1.0.
    * @return The quantiles of the data, that is, if t is the ith entry of the quantiles array, and x is the
    *         ith entry of returned quantiles array, then x is the minimum value in the input dataset so that
    *         a >= t fraction of the mass of the input is <= x.
    *         IE. x is the least x so that Pr ( X <= x ) >= t
    *         In the case of an empty input dataset, the quantile cutoffs returned are all positive infinity.
    */

  def computeQuantiles(data: RDD[Double], quantiles: Array[Double]): Array[Double] = {

    /*
     * Calculation is based on the fact that the quantile of a threshold t is the minimum x in the dataset so
     * that the empirical cumulative distribution at x is >= t.
     */

    def addDataPointToKnownCutoffs(cutoffs: Array[Double], newValueCDFPair: (Double, Double)): Array[Double] = {
      val newPoint = newValueCDFPair._1
      val cdfAtNewPoint = newValueCDFPair._2
      quantiles.zip(cutoffs).map({ case (threshold, oldCutoff) =>
        if (cdfAtNewPoint >= threshold && newPoint <= oldCutoff) newPoint else oldCutoff
      })
    }

    def mergeCutoffs(cuts1: Array[Double], cuts2: Array[Double]) = cuts1.zip(cuts2).map({ case (x, y) => min(x, y) })

    // Initial cutoffs are the trivial "positive infinity" cutoffs. These are the correct values for an empty data set.
    val initialCutoffs = Array.fill[Double](quantiles.length)(Double.PositiveInfinity)

    computeEcdf(data).aggregate(initialCutoffs)(addDataPointToKnownCutoffs, mergeCutoffs)
  }


  /**
    * Compute the deciles of a distribution.
    *
    * @param data RDD[Double] Incoming data.
    * @return Array[Double].  The deciles of the distribution.
    */

  def computeDeciles(data: RDD[Double]): Array[Double] = computeQuantiles(data, DECILES)
  val DECILES = Array(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0)
  /**
    * Compute the quintiles of a distribution.
    *
    * @param data RDD[Double] Incoming data.
    * @return Array[Double].  The quintiles of the distribution.
    */
  def computeQuintiles(data: RDD[Double]): Array[Double] = computeQuantiles(data, QUINTILES)
  val QUINTILES = Array(0.2, 0.4, 0.6, 0.8, 1.0)


  def bin(value: Double, cuts: Array[Double]) : Int = {
    cuts.indexWhere(cut => value <= cut)
  }
}