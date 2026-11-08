package app.arete.arete_api.model.dto.programtemplate;

/**
 * A template that can be imported: a folder under the catalog directory. Its path stays inside the catalog, so it
 * never reaches the API or the database.
 *
 * @param name         the folder's name, which is also the imported template's name
 * @param dayFileCount how many day files the folder holds
 */
public record ProgramTemplateCatalogEntry(
        String name,
        int dayFileCount
) {
}
