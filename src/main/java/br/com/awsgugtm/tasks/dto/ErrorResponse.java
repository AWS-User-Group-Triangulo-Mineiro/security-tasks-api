package br.com.awsgugtm.tasks.dto;

/**
 * Corpo de erro generico. Os valores devem vir sempre de constantes.
 */
public record ErrorResponse(String code, String message) {
}
