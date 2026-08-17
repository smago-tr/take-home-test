# Form schemas (reference contracts)

These are the two schemas from the original take-home spec, given here as reference documentation
only — not compiled Java classes. How you represent and validate them (POJO with Bean Validation,
Jackson `JsonNode` tree, a JSON Schema validator, etc.) is a design decision, not boilerplate.

## Ingested schema (as currently agreed with the external provider)

Not guaranteed to match reality — the 3rd party can change it without notice.

```
IngestedFormSchema {
  session_id: string
  application_reference: string
  name: string
  email: string
  gender: "male" | "female" | "other"
  date_of_birth: string
  phone_number: string | undefined
  mobile_number: string
  address: {
    address_line_1: string
    address_line_2: string
    address_line_3: string | undefined
    postcode: string
    country: string
  }
}
```

## Transformed schema (what the FORM-BOT expects)

```
TransformedFormSchema {
  sessionId: string
  applicationReference: string
  firstName: string
  lastName: string
  email: string
  gender: "male" | "female" | "prefer-not-to-say"
  dateOfBirth: Date
  phoneNumber: string | undefined
  mobileNumber: string
  addressLine1: string
  addressLine2: string
  addressLine3: string | undefined
  postcode: string
  country: string
  longitude: number
  latitude: number
}
```

Note `name` -> `firstName`/`lastName` requires a splitting decision, and `gender: "other"` has no
direct equivalent in the transformed enum.
