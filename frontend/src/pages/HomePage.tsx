import PerguntaForm from "../components/PerguntaForm";

const HomePage = () => {
  return (
    <>
      <h1 className="mb-2 text-xl font-semibold text-gray-900">Enviar uma pergunta</h1>
      <p className="mb-6 text-gray-700">
        Escreva uma dúvida de Ciência da Computação. Nesta etapa, o Gateway publica a pergunta no RabbitMQ e confirma o envio.
      </p>
      <PerguntaForm />
    </>
  );
};

export default HomePage;
