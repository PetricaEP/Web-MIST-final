package ep.db.model;

import java.io.Serializable;

/**
 * Classe represetando autor de um documento.
 * @version 1.0
 * @since 2017
 *
 */
public class Author implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -6489761035542574310L;

	/**
	 * ID do autor no banco de dados.
	 */
	private long id;
	
	/**
	 * Nome completo do autor
	 */
	private String name;
	
	/**
	 * Relevancia do autor
	 */
	private float rank;

	/**
	 * Cria novo autor
	 */
	public Author() {
		
	}
	
	/**
	 * Cria novo autor com o nome dado.
	 * @param name nome completo do autor.
	 */
	public Author(String name) {
		super();
		this.name = name;
	}

	/**
	 * Retorna id do autor no banco de dados.
	 * @return id do autor.
	 */
	public long getId() {
		return id;
	}

	/**
	 * Atribui id do autor.
	 * @param authorId id do autor
	 */
	public void setId(long authorId) {
		this.id = authorId;
	}

	/**
	 * Retorna nome do autor.
	 * @return nome completo do autor.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Atribui nome do autor.
	 * @param name nome completo do autor.
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * Retorna relevância do autor.
	 * @return relevância do autor (pagerank).
	 */
	public float getRank() {
		return rank;
	}
	
	/**
	 * Atribui relevância para o autor.
	 * @param rank relevância (pagerank).
	 */
	public void setRank(float rank) {
		this.rank = rank;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Author other = (Author) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
}
