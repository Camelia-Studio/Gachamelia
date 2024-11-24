package org.camelia.studio.gachamelia.services;

import org.camelia.studio.gachamelia.models.Element;
import org.camelia.studio.gachamelia.repositories.ElementRepository;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ElementService {
    private static ElementService instance;

    public static ElementService getInstance() {
        if (instance == null) {
            instance = new ElementService();
        }

        return instance;
    }

    public Element getRandomElement() {
        List<Element> elements = ElementRepository.getInstance().findAll();

        int percentage = ThreadLocalRandom.current().nextInt(elements.size()) + 1;
        int cumulativePercentage = 0;

        for (Element element : elements) {
            cumulativePercentage++;
            if (percentage <= cumulativePercentage) {
                return element;
            }
        }

        // Ne devrait jamais arriver
        return null;
    }
}
