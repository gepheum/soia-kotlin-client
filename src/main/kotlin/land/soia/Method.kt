package land.soia

/**
 * Represents a method in a Soia service, encapsulating the request and response types along with metadata.
 *
 * A method defines a specific operation that can be performed by a service, including the types
 * of data it accepts as input and returns as output.
 *
 * @param Request The type of the request parameter
 * @param Response The type of the response returned by this method
 * @param name The name of the method
 * @param number The unique number identifying this method in the service
 * @param requestSerializer Serializer for the request type
 * @param responseSerializer Serializer for the response type
 */
class Method<Request, Response>(
    val name: String,
    number: Int,
    requestSerializer: Serializer<Request>,
    responseSerializer: Serializer<Response>,
)
