/**
 * バックエンド {@link ApiErrorResponse} 相当の JSON を、HTTP ステータスとともに表す。
 */
export class GeoApiRequestError extends Error {
  readonly status: number;

  readonly errorCode: string;

  readonly details: Record<string, unknown> | undefined;

  constructor(
    status: number,
    errorCode: string,
    message: string,
    details?: Record<string, unknown>,
  ) {
    super(message);
    this.name = "GeoApiRequestError";
    this.status = status;
    this.errorCode = errorCode;
    this.details = details;
    Object.setPrototypeOf(this, new.target.prototype);
  }
}
