package com.tencent.angel.spark.vector

import scala.language.implicitConversions

import breeze.linalg.NumericOps
import breeze.linalg.dim
import breeze.linalg.norm
import breeze.linalg.operators._
import breeze.linalg.scaleAdd
import breeze.linalg.support.{CanCopy, CanCreateZerosLike}
import breeze.math.MutableLPVectorField
import org.apache.spark.SparkException

import com.tencent.angel.spark.func._
import com.tencent.angel.spark.PSClient
import com.tencent.angel.spark.PSVector
import com.tencent.angel.spark.PSVectorProxy



/**
 * BreezePSVector implements a set of operations among PSVectors. BreezePSVector tries to implement
 * all operation in `breeze.linalg.Vector`, it aims to reuse the breeze algorithm for BreezePSVector
 * type.
 *
 * BreezePSVector inherits from `breeze.linalg.NumericOps`, it implements a set of implicit
 * conversion to make `breeze.linalg.NumericOps` available to BreezePSVector.
 *
 * val a: BreezePSVector = pool.createZero().mkBreeze
 * val b: BreezePSVector = pool.createRandomUniform(0, 1).mkBreeze
 *
 * val c = a + b  // "+" is an operation in `breeze.linalg.NumericOps`
 */
class BreezePSVector private[spark](override val proxy: PSVectorProxy)
  extends PSVector with NumericOps[BreezePSVector] {

  // Ensure that operators are all loaded.
  BreezePSVector.init()

  override def repr: BreezePSVector = this

  override def toString: String = {
    PSClient.assertOnDriver()
    PSClient.get.get(proxy).mkString("BreezePSVector(", ", ", ")")
  }

  def toLocal: LocalPSVector = new LocalPSVector(proxy)

  def toRemote: RemotePSVector = new RemotePSVector(proxy)

  import BreezePSVector._


  // creation operators
  /**
   * Create a BreezePSVector filled with zero
   */
  def zerosLike: BreezePSVector = canCreateZerosLike(this)

  def copy: BreezePSVector = canCopyBreezePSVector(this)

  /**
   * Create a BreezePSVector filled with `value`
   */
  def fillLike(value: Double): BreezePSVector = {
    proxy.getPool().create(value).mkBreeze()
  }

  /**
   * Create a BreezePSVector filled with 1.0
   */
  def onesLike: BreezePSVector = {
    fillLike(1.0)
  }

  /**
   * Create a BreezePSVector with Double array
   */
  def createLike(value: Array[Double]): BreezePSVector = {
    proxy.getPool().create(value).mkBreeze()
  }

  /**
   * Create a random BreezePSVector, the elements is generated by uniform distribution
   *
   * @param min the uniform distribution parameter: minimum boundary
   * @param max the uniform distribution parameter: maximum boundary
   */
  def randomUniformLike(min: Double, max: Double): BreezePSVector = {
    proxy.getPool().createRandomUniform(min, max).mkBreeze()
  }

  /**
   * Create a random BreezePSVector, the elements is generated by normal distribution
   *
   * @param mean the uniform distribution parameter: mean
   * @param stddev the uniform distribution parameter: standard deviation
   */
  def randomNormalLike(mean: Double, stddev: Double): BreezePSVector = {
    proxy.getPool().createRandomNormal(mean, stddev).mkBreeze()
  }


  // aggregators

  /**
   * Calculate p-norm of BreezePSVector
   */
  def norm(p: Int): Double = canNorm2(this, p)

  /**
   * Find the maximum element in BreezePSVector
   */
  def max: Double = PSClient.get.max(proxy)

  /**
   * Find the minimum element in BreezePSVector
   */
  def min: Double = PSClient.get.min(proxy)

  /**
   * Calculate summation of each BreezePSVector element
   */
  def sum: Double = PSClient.get.sum(proxy)


  // functional operators
  /**
   * Apply `MapFunc` to each element of BreezePSVector
   */
  def map(func: MapFunc): BreezePSVector = {
    val to = proxy.getPool().allocate()
    PSClient.get.map(proxy, func, to)
    to.mkBreeze()
  }

  /**
   * Apply `Zip2MapFunc` to this and `other` BreezePSVector
   */
  def zipMap(other: BreezePSVector, func: Zip2MapFunc): BreezePSVector = {
    val to = proxy.getPool().allocate()
    PSClient.get.zip2Map(proxy, other.proxy, func, to)
    to.mkBreeze()
  }

  /**
   * Apply `Zip3MapFunc` to this, `other1` and `other2` BreezePSVector
   */
  def zipMap(
      other1: BreezePSVector,
      other2: BreezePSVector,
      func: Zip3MapFunc): BreezePSVector = {
    val to = proxy.getPool().allocate()
    PSClient.get.zip3Map(proxy, other1.proxy, other2.proxy, func, to)
    to.mkBreeze()
  }

  /**
   * Apply `MapWithIndexFunc` to each element of BreezePSVector
   */
  def mapWithIndex(func: MapWithIndexFunc): BreezePSVector = {
    val to = proxy.getPool().allocate()
    PSClient.get.mapWithIndex(proxy, func, to)
    to.mkBreeze()
  }

  /**
   * Apply `Zip2MapWithIndexFunc` to this and `other` BreezePSVector
   */
  def zipMapWithIndex(
      other: BreezePSVector,
      func: Zip2MapWithIndexFunc): BreezePSVector = {
    val to = proxy.getPool().allocate()
    PSClient.get.zip2MapWithIndex(proxy, other.proxy, func, to)
    to.mkBreeze()
  }

  /**
   * Apply `Zip3MapWithIndexFunc` to this, `other1` and `other2` BreezePSVector
   */
  def zipMapWithIndex(
      other1: BreezePSVector,
      other2: BreezePSVector,
      func: Zip3MapWithIndexFunc): BreezePSVector = {
    val to = proxy.getPool().allocate()
    PSClient.get.zip3MapWithIndex(proxy, other1.proxy, other2.proxy, func, to)
    to.mkBreeze()
  }

  // mutable functional operators
  /**
   * Apply `MapFunc` to each element of BreezePSVector,
   * and save the result in this PSBreezeVector
   */
  def mapInto(func: MapFunc): Unit = {
    PSClient.get.map(proxy, func, proxy)
  }

  /**
   * Apply `Zip2MapFunc` to this and `other` BreezePSVector,
   * and save the result in this PSBreezeVector
   */
  def zipMapInto(other: BreezePSVector, func: Zip2MapFunc): Unit = {
    PSClient.get.zip2Map(proxy, other.proxy, func, proxy)
  }

  /**
   * Apply `Zip3MapFunc` to this, `other1` and `other2` BreezePSVector,
   * and save the result in this PSBreezeVector
   */
  def zipMapInto(
      other1: BreezePSVector,
      other2: BreezePSVector,
      func: Zip3MapFunc): Unit = {
    PSClient.get.zip3Map(proxy, other1.proxy, other2.proxy, func, proxy)
  }

  /**
   * Apply `MapWithIndexFunc` to each element of BreezePSVector,
   * and save the result in this PSBreezeVector
   */
  def mapWithIndexInto(func: MapWithIndexFunc): Unit = {
    PSClient.get.mapWithIndex(proxy, func, proxy)
  }

  /**
   * Apply `Zip2MapWithIndexFunc` to this and `other` BreezePSVector,
   * and save the result in this PSBreezeVector
   */
  def zipMapWithIndexInto(other: BreezePSVector, func: Zip2MapWithIndexFunc): Unit = {
    PSClient.get.zip2MapWithIndex(proxy, other.proxy, func, proxy)
  }
  /**
   * Apply `Zip3MapWithIndexFunc` to this, `other1` and `other2` BreezePSVector,
   * and save the result in this PSBreezeVector
   */
  def zipMapWithIndexInto(
      other1: BreezePSVector,
      other2: BreezePSVector,
      func: Zip3MapWithIndexFunc): Unit = {
    PSClient.get.zip3MapWithIndex(proxy, other1.proxy, other2.proxy, func, proxy)
  }

}

object BreezePSVector {

  /**
   * Operations in math for BreezePSVector is corresponding to `scala.math`
   */
  // scalastyle:off
  object math {
    def max(x: BreezePSVector, y: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.max(x.proxy, y.proxy, to)
      to.mkBreeze()
    }

    def min(x: BreezePSVector, y: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.min(x.proxy, y.proxy, to)
      to.mkBreeze()
    }

    def pow(x: BreezePSVector, a: Double): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.pow(x.proxy, a, to)
      to.mkBreeze()
    }

    def sqrt(x: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.sqrt(x.proxy, to)
      to.mkBreeze()
    }

    def exp(x: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.exp(x.proxy, to)
      to.mkBreeze()
    }

    def expm1(x: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.expm1(x.proxy, to)
      to.mkBreeze()
    }

    def log(x: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.log(x.proxy, to)
      to.mkBreeze()
    }

    def log1p(x: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.log1p(x.proxy, to)
      to.mkBreeze()
    }

    def log10(x: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.log10(x.proxy, to)
      to.mkBreeze()
    }

    def ceil(x: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.ceil(x.proxy, to)
      to.mkBreeze()
    }

    def floor(x: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.floor(x.proxy, to)
      to.mkBreeze()
    }

    def round(x: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.round(x.proxy, to)
      to.mkBreeze()
    }

    def abs(x: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.abs(x.proxy, to)
      to.mkBreeze()
    }

    def signum(x: BreezePSVector): BreezePSVector = {
      val to = x.proxy.getPool().allocate()
      PSClient.get.signum(x.proxy, to)
      to.mkBreeze()
    }


    // in place funcs

    def maxInto(x: BreezePSVector, y: BreezePSVector): Unit = {
      PSClient.get.max(x.proxy, y.proxy, x.proxy)
    }

    def minInto(x: BreezePSVector, y: BreezePSVector): Unit = {
      PSClient.get.min(x.proxy, y.proxy, x.proxy)
    }

    def powInto(x: BreezePSVector, a: Double): Unit = {
      PSClient.get.pow(x.proxy, a, x.proxy)
    }

    def sqrtInto(x: BreezePSVector): Unit = {
      PSClient.get.sqrt(x.proxy, x.proxy)
    }

    def expInto(x: BreezePSVector): Unit = {
      PSClient.get.exp(x.proxy, x.proxy)
    }

    def expm1Into(x: BreezePSVector): Unit = {
      PSClient.get.expm1(x.proxy, x.proxy)
    }

    def logInto(x: BreezePSVector): Unit = {
      PSClient.get.log(x.proxy, x.proxy)
    }

    def log1pInto(x: BreezePSVector): Unit = {
      PSClient.get.log1p(x.proxy, x.proxy)
    }

    def log10Into(x: BreezePSVector): Unit = {
      PSClient.get.log10(x.proxy, x.proxy)
    }

    def ceilInto(x: BreezePSVector): Unit = {
      PSClient.get.ceil(x.proxy, x.proxy)
    }

    def floorInto(x: BreezePSVector): Unit = {
      PSClient.get.floor(x.proxy, x.proxy)
    }

    def roundInto(x: BreezePSVector): Unit = {
      PSClient.get.round(x.proxy, x.proxy)
    }

    def absInto(x: BreezePSVector): Unit = {
      PSClient.get.abs(x.proxy, x.proxy)
    }

    def signumInto(x: BreezePSVector): Unit = {
      PSClient.get.signum(x.proxy, x.proxy)
    }
  }


  /**
   * These are blas operations for BreezePSVector
   */
  object blas {
    def axpy(a: Double, x: BreezePSVector, y: BreezePSVector): Unit = {
      PSClient.get.axpy(a, x.proxy, y.proxy)
    }

    def dot(x: BreezePSVector, y: BreezePSVector): Double = {
      PSClient.get.dot(x.proxy, y.proxy)
    }

    def copy(x: BreezePSVector, y: BreezePSVector): Unit = {
      PSClient.get.copy(x.proxy, y.proxy)
    }

    def scal(a: Double, x: BreezePSVector): Unit = {
      PSClient.get.scal(a, x.proxy)
    }

    def nrm2(x: BreezePSVector): Double = {
      PSClient.get.nrm2(x.proxy)
    }

    def asum(x: BreezePSVector): Double = {
      PSClient.get.asum(x.proxy)
    }

    def amax(x: BreezePSVector): Double = {
      PSClient.get.amax(x.proxy)
    }

    def amin(x: BreezePSVector): Double = {
      PSClient.get.amin(x.proxy)
    }
  }
  // scalastyle:on


  // capabilities

  implicit val canCreateZerosLike: CanCreateZerosLike[BreezePSVector, BreezePSVector] =
    new CanCreateZerosLike[BreezePSVector, BreezePSVector] {
      def apply(v: BreezePSVector): BreezePSVector = {
        v.proxy.getPool().createZero().mkBreeze()
      }
    }

  implicit val canCopyBreezePSVector: CanCopy[BreezePSVector] = {
    new CanCopy[BreezePSVector] {
      def apply(v: BreezePSVector): BreezePSVector = {
        val r = v.proxy.getPool().allocate()
        PSClient.get.copy(v.proxy, r)
        r.mkBreeze()
      }
    }
  }

  implicit val canSetInto: OpSet.InPlaceImpl2[BreezePSVector, BreezePSVector] = {
    new OpSet.InPlaceImpl2[BreezePSVector, BreezePSVector] {
      def apply(y: BreezePSVector, x: BreezePSVector): Unit = {
        PSClient.get.copy(x.proxy, y.proxy)
      }
    }
  }

  implicit val canSetIntoS: OpSet.InPlaceImpl2[BreezePSVector, Double] = {
    new OpSet.InPlaceImpl2[BreezePSVector, Double] {
      def apply(a: BreezePSVector, b: Double): Unit = {
        PSClient.get.fill(a.proxy, b)
      }
    }
  }

  implicit val canAxpy: scaleAdd.InPlaceImpl3[BreezePSVector, Double, BreezePSVector] = {
    new scaleAdd.InPlaceImpl3[BreezePSVector, Double, BreezePSVector] {
      def apply(y: BreezePSVector, a: Double, x: BreezePSVector): Unit = {
        PSClient.get.axpy(a, x.proxy, y.proxy)
      }
    }
  }

  implicit val canAddInto: OpAdd.InPlaceImpl2[BreezePSVector, BreezePSVector] = {
    new OpAdd.InPlaceImpl2[BreezePSVector, BreezePSVector] {
      def apply(a: BreezePSVector, b: BreezePSVector): Unit = {
        PSClient.get.add(a.proxy, b.proxy, a.proxy)
      }
    }
  }

  implicit val canAdd: OpAdd.Impl2[BreezePSVector, BreezePSVector, BreezePSVector] = {
    new OpAdd.Impl2[BreezePSVector, BreezePSVector, BreezePSVector] {
      def apply(a: BreezePSVector, b: BreezePSVector): BreezePSVector = {
        val to = a.proxy.getPool().allocate()
        PSClient.get.add(a.proxy, b.proxy, to)
        to.mkBreeze()
      }
    }
  }

  implicit val canAddIntoS: OpAdd.InPlaceImpl2[BreezePSVector, Double] = {
    new OpAdd.InPlaceImpl2[BreezePSVector, Double] {
      def apply(a: BreezePSVector, b: Double): Unit = {
        PSClient.get.add(a.proxy, b, a.proxy)
      }
    }
  }

  implicit val canAddS: OpAdd.Impl2[BreezePSVector, Double, BreezePSVector] = {
    new OpAdd.Impl2[BreezePSVector, Double, BreezePSVector] {
      def apply(a: BreezePSVector, b: Double): BreezePSVector = {
        val to = a.proxy.getPool().allocate()
        PSClient.get.add(a.proxy, b, to)
        to.mkBreeze()
      }
    }
  }

  implicit val canSubInto: OpSub.InPlaceImpl2[BreezePSVector, BreezePSVector] = {
    new OpSub.InPlaceImpl2[BreezePSVector, BreezePSVector] {
      def apply(a: BreezePSVector, b: BreezePSVector): Unit = {
        PSClient.get.sub(a.proxy, b.proxy, a.proxy)
      }
    }
  }

  implicit val canSub: OpSub.Impl2[BreezePSVector, BreezePSVector, BreezePSVector] = {
    new OpSub.Impl2[BreezePSVector, BreezePSVector, BreezePSVector] {
      def apply(a: BreezePSVector, b: BreezePSVector): BreezePSVector = {
        val to = a.proxy.getPool().allocate()
        PSClient.get.sub(a.proxy, b.proxy, to)
        to.mkBreeze()
      }
    }
  }

  implicit val canSubIntoS: OpSub.InPlaceImpl2[BreezePSVector, Double] = {
    new OpSub.InPlaceImpl2[BreezePSVector, Double] {
      def apply(a: BreezePSVector, b: Double): Unit = {
        PSClient.get.sub(a.proxy, b, a.proxy)
      }
    }
  }

  implicit val canSubS: OpSub.Impl2[BreezePSVector, Double, BreezePSVector] = {
    new OpSub.Impl2[BreezePSVector, Double, BreezePSVector] {
      def apply(a: BreezePSVector, b: Double): BreezePSVector = {
        val to = a.proxy.getPool().allocate()
        PSClient.get.sub(a.proxy, b, to)
        to.mkBreeze()
      }
    }
  }

  implicit val canMulInto: OpMulScalar.InPlaceImpl2[BreezePSVector, BreezePSVector] = {
    new OpMulScalar.InPlaceImpl2[BreezePSVector, BreezePSVector] {
      def apply(a: BreezePSVector, b: BreezePSVector): Unit = {
        PSClient.get.mul(a.proxy, b.proxy, a.proxy)
      }
    }
  }

  implicit val canMul: OpMulScalar.Impl2[BreezePSVector, BreezePSVector, BreezePSVector] = {
    new OpMulScalar.Impl2[BreezePSVector, BreezePSVector, BreezePSVector] {
      def apply(a: BreezePSVector, b: BreezePSVector): BreezePSVector = {
        val to = a.proxy.getPool().allocate()
        PSClient.get.mul(a.proxy, b.proxy, to)
        to.mkBreeze()
      }
    }
  }

  implicit val canMulIntoS: OpMulScalar.InPlaceImpl2[BreezePSVector, Double] = {
    new OpMulScalar.InPlaceImpl2[BreezePSVector, Double] {
      def apply(a: BreezePSVector, b: Double): Unit = {
        PSClient.get.mul(a.proxy, b, a.proxy)
      }
    }
  }

  implicit val canMulS: OpMulScalar.Impl2[BreezePSVector, Double, BreezePSVector] = {
    new OpMulScalar.Impl2[BreezePSVector, Double, BreezePSVector] {
      def apply(a: BreezePSVector, b: Double): BreezePSVector = {
        val to = a.proxy.getPool().allocate()
        PSClient.get.mul(a.proxy, b, to)
        to.mkBreeze()
      }
    }
  }

  implicit val negFromScale: OpNeg.Impl[BreezePSVector, BreezePSVector] = {
    val scale = implicitly[OpMulScalar.Impl2[BreezePSVector, Double, BreezePSVector]]
    new OpNeg.Impl[BreezePSVector, BreezePSVector] {
      def apply(a: BreezePSVector): BreezePSVector = {
        scale(a, -1.0)
      }
    }
  }

  implicit val canDivInto: OpDiv.InPlaceImpl2[BreezePSVector, BreezePSVector] = {
    new OpDiv.InPlaceImpl2[BreezePSVector, BreezePSVector] {
      def apply(a: BreezePSVector, b: BreezePSVector): Unit = {
        PSClient.get.div(a.proxy, b.proxy, a.proxy)
      }
    }
  }

  implicit val canDiv: OpDiv.Impl2[BreezePSVector, BreezePSVector, BreezePSVector] = {
    new OpDiv.Impl2[BreezePSVector, BreezePSVector, BreezePSVector] {
      def apply(a: BreezePSVector, b: BreezePSVector): BreezePSVector = {
        val to = a.proxy.getPool().allocate()
        PSClient.get.div(a.proxy, b.proxy, to)
        to.mkBreeze()
      }
    }
  }

  implicit val canDivIntoS: OpDiv.InPlaceImpl2[BreezePSVector, Double] = {
    new OpDiv.InPlaceImpl2[BreezePSVector, Double] {
      def apply(a: BreezePSVector, b: Double): Unit = {
        PSClient.get.div(a.proxy, b, a.proxy)
      }
    }
  }

  implicit val canDivS: OpDiv.Impl2[BreezePSVector, Double, BreezePSVector] = {
    new OpDiv.Impl2[BreezePSVector, Double, BreezePSVector] {
      def apply(a: BreezePSVector, b: Double): BreezePSVector = {
        val to = a.proxy.getPool().allocate()
        PSClient.get.div(a.proxy, b, to)
        to.mkBreeze()
      }
    }
  }

  implicit val canPow: OpPow.Impl2[BreezePSVector, Double, BreezePSVector] = {
    new OpPow.Impl2[BreezePSVector, Double, BreezePSVector] {
      def apply(a: BreezePSVector, b: Double): BreezePSVector = {
        val to = a.proxy.getPool().allocate()
        PSClient.get.pow(a.proxy, b, to)
        to.mkBreeze()
      }
    }
  }

  implicit val canDot: OpMulInner.Impl2[BreezePSVector, BreezePSVector, Double] = {
    new OpMulInner.Impl2[BreezePSVector, BreezePSVector, Double] {
      def apply(a: BreezePSVector, b: BreezePSVector): Double = {
        PSClient.get.dot(a.proxy, b.proxy)
      }
    }
  }

  /**
   * Returns the 2-norm of this Vector.
   */
  implicit val canNorm: norm.Impl[BreezePSVector, Double] = {
    new norm.Impl[BreezePSVector, Double] {
      def apply(v: BreezePSVector): Double = {
        PSClient.get.nrm2(v.proxy)
      }
    }
  }

  /**
   * Returns the p-norm of this Vector.
   */
  implicit val canNorm2: norm.Impl2[BreezePSVector, Double, Double] = {
    new norm.Impl2[BreezePSVector, Double, Double] {
      def apply(v: BreezePSVector, p: Double): Double = {
        if (p == 2) {
          PSClient.get.nrm2(v.proxy)
        } else if (p == 1) {
          PSClient.get.asum(v.proxy)
        } else if (p == Double.PositiveInfinity) {
          PSClient.get.amax(v.proxy)
        } else if (p == 0) {
          PSClient.get.nnz(v.proxy)
        } else {
          throw new SparkException("Dose not support p-norms other than L0, L1, L2 and Linf")
        }
      }
    }
  }

  implicit val canDim: dim.Impl[BreezePSVector, Int] = new dim.Impl[BreezePSVector, Int] {
    def apply(v: BreezePSVector): Int = v.proxy.numDimensions
  }

  implicit val space: MutableLPVectorField[BreezePSVector, Double] = {
    MutableLPVectorField.make[BreezePSVector, Double]
  }

  // used to make sure the operators are loaded
  @noinline
  private def init() = {}
}
