package kr.hhplus.be.server.common.extension

/**
 * null이 아닐 경우 예외를 던지는 확장 함수
 *
 * @param T 대상 타입
 * @param exception 던질 예외
 * @throws Exception 대상이 null이 아닐 경우
 */
fun <T> T?.throwIfNotNull(exception: Exception) {
    if (this != null) {
        throw exception
    }
}