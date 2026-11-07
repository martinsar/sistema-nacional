package ar.edu.unlp.sistemanacional.sistema_nacional;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OngCodigoInitializer implements ApplicationRunner {

    private final OngRepository ongRepository;

    public OngCodigoInitializer(OngRepository ongRepository) {
        this.ongRepository = ongRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var ongs = ongRepository.findAllByCodigoOngIsNull();
        for (Ong ong : ongs) {
            ong.generarCodigoOngSiFalta();
        }
        ongRepository.saveAll(ongs);
    }
}
