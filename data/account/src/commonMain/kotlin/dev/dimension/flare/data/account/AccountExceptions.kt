package dev.dimension.flare.data.account

public data object NoActiveAccountException : Exception("No active account.")

public typealias LoginExpiredException = dev.dimension.flare.model.LoginExpiredException

public typealias RequireReLoginException = dev.dimension.flare.model.RequireReLoginException
