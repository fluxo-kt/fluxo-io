package fluxo.io

public actual open class IOException actual constructor(message: String) : Exception(message)

public actual open class EOFException
actual constructor(message: String) : IOException(message)
