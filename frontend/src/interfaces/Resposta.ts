export interface Resposta {
  correlation_id: string;
  texto: string;
  status: "OK" | "ERRO";
}