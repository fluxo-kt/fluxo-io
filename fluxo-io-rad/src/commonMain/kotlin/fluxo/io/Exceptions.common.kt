@file:Suppress("KDocUnresolvedReference")

package fluxo.io

/**
 * Signals about a general issue occurred during I/O operation.
 *
 * @see java.io.IOException
 */
public expect open class IOException : Exception

/**
 * Signals that the end of the file or stream was reached unexpectedly during an input operation.
 *
 * @see java.io.EOFException
 */
public expect open class EOFException(message: String) : IOException
