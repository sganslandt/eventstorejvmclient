package eventstore
package tcp

import akka.io._
import EventStoreFormats._
import util.{ BytesWriter, BytesReader }

/**
 * @author Yaroslav Klymko
 */
class MessageByteStringAdapter
    extends PipelineStage[PipelineContext, TcpPackageOut, ByteString, TcpPackageIn, ByteString] {

  def apply(ctx: PipelineContext) = new PipePair[TcpPackageOut, ByteString, TcpPackageIn, ByteString] {
    val commandPipeline = (x: TcpPackageOut) ⇒ ctx.singleCommand(BytesWriter[TcpPackageOut].toByteString(x))
    val eventPipeline = (bs: ByteString) ⇒ ctx.singleEvent(BytesReader[TcpPackageIn].read(bs))
  }
}