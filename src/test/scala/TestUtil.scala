/*
** 2025 May 1
**
** The author disclaims copyright to this source code.  In place of
** a legal notice, here is a blessing:
**
**    May you do good and not evil.
**    May you find forgiveness for yourself and forgive others.
**    May you share freely, never taking more than you give.
**
 */
package testutils

/** 提供測試常用方法與配置
  */
trait HasDebugPrint {

  /* 默認開啓, 當模塊測試完後, 請在模塊內修改關閉 */
  var debugPrint = true
  var tracePrint = false
  def dprintln(x: => Any): Unit = {
    if (debugPrint) println(x)
  }
  def tprintln(x: => Any): Unit = {
    if (tracePrint) println(x)
  }
}
