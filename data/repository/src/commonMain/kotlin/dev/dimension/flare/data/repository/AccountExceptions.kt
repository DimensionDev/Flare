package dev.dimension.flare.data.repository

public data object NoActiveAccountException : Exception("No active account.")

public typealias LoginExpiredException = dev.dimension.flare.model.LoginExpiredException

public typealias RequireReLoginException = dev.dimension.flare.model.RequireReLoginException
