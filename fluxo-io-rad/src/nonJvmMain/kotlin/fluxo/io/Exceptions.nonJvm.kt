package fluxo.io

public actual open class IOException(message: String) : Exception(message)

public actual open class EOFException
actual constructor(message: String) : IOException(message)
