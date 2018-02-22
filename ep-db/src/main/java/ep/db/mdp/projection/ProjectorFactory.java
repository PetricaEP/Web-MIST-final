package ep.db.mdp.projection;

public class ProjectorFactory {

	public static Projector getInstance(ProjectorType type) {

        if (type.equals(ProjectorType.FASTMAP)) {
            return new FastmapProjection();
        } else if (type.equals(ProjectorType.NNP)) {
            return new NearestNeighborProjection();
        }

        return null;
    }
}
