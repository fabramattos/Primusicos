package br.com.arpeggio.api.service

import br.com.arpeggio.api.dto.externalApi.deezer.*
import br.com.arpeggio.api.dto.request.RequestParams
import br.com.arpeggio.api.dto.response.AlbumsResponse
import br.com.arpeggio.api.dto.response.ExternalErrorResponse
import br.com.arpeggio.api.dto.response.PodcastsResponse
import br.com.arpeggio.api.dto.response.SearchResults
import br.com.arpeggio.api.infra.exception.*
import br.com.arpeggio.api.infra.log.Logs
import com.google.common.net.HttpHeaders
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import kotlin.coroutines.coroutineContext


@Service
class DeezerService(
    override val NOME_STREAMING: String = "Deezer",
    val webClient: WebClient = WebClient.builder()
        .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
        .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
        .build()
) : CommandStreamingAudio {

    

    private suspend fun buscaArtista(nome: String): DeezerApiArtistData {
        val uri = UriComponentsBuilder
            .fromUriString("https://api.deezer.com/search/artist")
            .queryParam("q", nome)
            .buildAndExpand()
            .toUri()

        return webClient
            .get()
            .uri{uri}
            .retrieve()
            .bodyToMono<DeezerApiArtistsResponse>()
            .map { it.data }
            .awaitSingleOrNull()
            ?.firstOrNull()
            ?: throw ArtistaNaoEncontradoException()
    }

    private suspend fun buscaPodcasts(nome: String): DeezerApiPodcastData {
        val uri = UriComponentsBuilder
            .fromUriString("https://api.deezer.com/search/podcast")
            .queryParam("q", nome)
            .buildAndExpand()
            .toUri()

        return webClient
            .get()
            .uri{uri}
            .retrieve()
            .bodyToMono<DeezerApiPodcastsResponse>()
            .map { it.data }
            .awaitSingleOrNull()
            ?.firstOrNull()
            ?: throw PodcastNaoEncontradoException()
    }

    private suspend fun buscaAlbunsDoArtista(requestParams: RequestParams, idArtista: Int): List<DeezerApiAlbumData> {
        val uri = UriComponentsBuilder
            .fromUriString("https://api.deezer.com/artist/$idArtista/albums")
            .queryParam("limit", 999)
            .buildAndExpand()
            .toUri()

        return webClient
            .get()
            .uri{uri}
            .retrieve()
            .bodyToMono<DeezerApiAlbumsResponse>()
            .map { it.data }
            .awaitSingleOrNull()
            ?.filter { album ->
                requestParams.tipos.any { album.record_type.equals(it.name, true) }
            }
            ?: throw FalhaAoBuscarAlbunsDoArtista()
    }

    private suspend fun buscaEpisodiosDoPodcast(idPodcast: Int): Int {
        val uri = UriComponentsBuilder
            .fromUriString("https://api.deezer.com/podcast/$idPodcast/episodes")
            .buildAndExpand()
            .toUri()

        //TODO remover debug
        val json: String = webClient
            .get()
            .uri{uri}
            .retrieve()
            .bodyToMono<String>()
            .awaitSingleOrNull()
            ?:"XABLAU2"

        Logs.debug("BUSCA EPISODIOS: $json");

        return webClient
            .get()
            .uri{uri}
            .retrieve()
            .bodyToMono<DeezerApiAlbumsResponse>()
            .map { it.total }
            .awaitSingleOrNull()
            ?: throw FalhaAoBuscarPodcastsException()
    }


    override suspend fun buscaPorArtista(requestParams: RequestParams): SearchResults {
        var erros = 0
        while(erros < 3){
            val response = runCatching {
                val artista = buscaArtista(requestParams.busca)
                val totalDeAlbuns = buscaAlbunsDoArtista(requestParams, artista.id).size
                return AlbumsResponse(NOME_STREAMING, artista.name, totalDeAlbuns)
            }

            response.onFailure {
                erros++
                Logs.warn(NOME_STREAMING, requestParams.id.toString(), it.localizedMessage, erros)
                if(it is ArtistaNaoEncontradoException)
                    return ExternalErrorResponse(NOME_STREAMING, it.localizedMessage)

                Thread.sleep(1000)
            }
        }
        return ExternalErrorResponse(NOME_STREAMING, FalhaNaRequisicaoAoStreamingException(NOME_STREAMING).localizedMessage)
    }

    override suspend fun buscaPorPodcast(requestParams: RequestParams): SearchResults {
        var erros = 0
        while(erros < 3){
            val response = runCatching {
                val podcast = buscaPodcasts(requestParams.busca)
                val totalDeEpisodios = buscaEpisodiosDoPodcast(podcast.id)
                return PodcastsResponse(NOME_STREAMING, podcast.title, totalDeEpisodios)
            }

            response.onFailure {
                erros++
                Logs.warn(NOME_STREAMING, requestParams.id.toString(), it.localizedMessage, erros)
                if(it is PodcastNaoEncontradoException)
                    return ExternalErrorResponse(NOME_STREAMING, it.localizedMessage)

                Thread.sleep(1000)
            }
        }
        return ExternalErrorResponse(NOME_STREAMING, FalhaNaRequisicaoAoStreamingException(NOME_STREAMING).localizedMessage)
    }

}





